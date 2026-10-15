#!/usr/bin/env node

/**
 * Imports Red Hat CSAF advisories into a CSAF CMS Backend test database.
 *
 * This script uses only Node.js built-ins. It downloads and verifies Red Hat's
 * daily CSAF archive, extracts it, and imports documents through the backend
 * API. Successful results can be skipped on a later run with --resume.
 */

'use strict';

const { createHash } = require('node:crypto');
const { createWriteStream, existsSync } = require('node:fs');
const fs = require('node:fs/promises');
const { dirname, join, relative, sep } = require('node:path');
const { spawn } = require('node:child_process');
const { pipeline } = require('node:stream/promises');

const SCRIPT_DIRECTORY = __dirname;
const WORK_DIRECTORY = join(SCRIPT_DIRECTORY, 'redhat-advisories');
const PROVIDER_BASE_URL = 'https://security.access.redhat.com/data/csaf/v2/advisories';
const MAX_DOCUMENTS = 29817;

function localDateYesterday() {
    const date = new Date();
    date.setDate(date.getDate() - 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function usage() {
    return `Usage: node jmeter/import-redhat-advisories.js [options]

Downloads Red Hat CSAF advisories, verifies the archive checksum, and imports
documents sequentially into the CSAF CMS Backend.

Options:
  --archive-date YYYY-MM-DD   Red Hat archive date (default: yesterday)
  --api-base-url URL          Backend API base URL (default: http://localhost:8081/api/v1)
  --keycloak-base-url URL     Keycloak base URL (default: http://localhost:9000)
  --realm NAME                Keycloak realm (default: csaf)
  --client-id ID              Keycloak client ID (default: secvisogram)
  --client-secret SECRET      Keycloak client secret
  --username NAME             Keycloak username (default: publisher)
  --password PASSWORD         Keycloak password (default: publisher)
  --limit NUMBER              Import at most NUMBER documents (0 imports all)
  --resume                    Skip documents whose latest result is Success and recover interrupted imports
  --skip-download             Reuse the existing extracted data directory
  --data-directory PATH       Directory containing extracted JSON documents
  --state-file PATH           CSV file for import results
  --timeout-seconds NUMBER    Per HTTP request timeout, 10 to 3600 (default: 300)
  --help                      Show this help text

Examples:
  node jmeter/import-redhat-advisories.js --limit 10
  node jmeter/import-redhat-advisories.js --archive-date 2026-10-11 --resume
`;
}

function parseArguments(args) {
    const options = {
        archiveDate: localDateYesterday(),
        apiBaseUrl: 'http://localhost:8081/api/v1',
        keycloakBaseUrl: 'http://localhost:9000',
        realm: 'csaf',
        clientId: 'secvisogram',
        clientSecret: 'dev-only-3f8a1c7e2b9d4f6a0e5c8b3d7a1f9e2c',
        username: 'publisher',
        password: 'publisher',
        limit: 0,
        resume: false,
        skipDownload: false,
        dataDirectory: '',
        stateFile: '',
        timeoutSeconds: 300
    };
    const valueOptions = new Map([
        ['--archive-date', 'archiveDate'],
        ['--api-base-url', 'apiBaseUrl'],
        ['--keycloak-base-url', 'keycloakBaseUrl'],
        ['--realm', 'realm'],
        ['--client-id', 'clientId'],
        ['--client-secret', 'clientSecret'],
        ['--username', 'username'],
        ['--password', 'password'],
        ['--limit', 'limit'],
        ['--data-directory', 'dataDirectory'],
        ['--state-file', 'stateFile'],
        ['--timeout-seconds', 'timeoutSeconds']
    ]);

    for (let index = 0; index < args.length; index += 1) {
        const argument = args[index];
        if (argument === '--help') {
            console.log(usage());
            process.exit(0);
        }
        if (argument === '--resume') {
            options.resume = true;
            continue;
        }
        if (argument === '--skip-download') {
            options.skipDownload = true;
            continue;
        }
        const optionName = valueOptions.get(argument);
        if (!optionName) {
            throw new Error(`Unknown option: ${argument}\n\n${usage()}`);
        }
        index += 1;
        if (index >= args.length || args[index].startsWith('--')) {
            throw new Error(`Option ${argument} requires a value.`);
        }
        options[optionName] = args[index];
    }

    if (!/^\d{4}-\d{2}-\d{2}$/.test(options.archiveDate)) {
        throw new Error('--archive-date must use the YYYY-MM-DD format.');
    }
    options.limit = parseIntegerOption('--limit', options.limit, 0, MAX_DOCUMENTS);
    options.timeoutSeconds = parseIntegerOption('--timeout-seconds', options.timeoutSeconds, 10, 3600);
    return options;
}

function parseIntegerOption(name, value, minimum, maximum) {
    if (!/^\d+$/.test(String(value))) {
        throw new Error(`${name} must be an integer between ${minimum} and ${maximum}.`);
    }
    const number = Number(value);
    if (!Number.isSafeInteger(number) || number < minimum || number > maximum) {
        throw new Error(`${name} must be an integer between ${minimum} and ${maximum}.`);
    }
    return number;
}

async function fetchOrThrow(url, options, timeoutSeconds) {
    const response = await fetch(url, { ...options, signal: AbortSignal.timeout(timeoutSeconds * 1000) });
    if (!response.ok) {
        const responseBody = (await response.text()).replaceAll(/\s+/g, ' ').trim();
        const error = new Error(`HTTP ${response.status} ${response.statusText}${responseBody ? `: ${responseBody}` : ''}`);
        error.httpStatus = response.status;
        throw error;
    }
    return response;
}

async function downloadFile(url, destination, timeoutSeconds) {
    const response = await fetchOrThrow(url, {}, timeoutSeconds);
    if (!response.body) {
        throw new Error(`Download response did not contain a body: ${url}`);
    }
    await pipeline(response.body, createWriteStream(destination));
}

async function verifyChecksum(archivePath, checksumPath) {
    const checksum = (await fs.readFile(checksumPath, 'utf8')).trim().split(/\s+/)[0];
    const hash = createHash('sha256');
    const archive = await fs.open(archivePath);
    try {
        await pipeline(archive.createReadStream(), hash);
    } finally {
        await archive.close();
    }
    const actualChecksum = hash.digest('hex');
    if (actualChecksum.toLowerCase() !== checksum.toLowerCase()) {
        throw new Error(`Checksum validation failed for ${archivePath}. Expected ${checksum} but received ${actualChecksum}.`);
    }
    console.log('SHA-256 checksum verified.');
}

function run(command, args, options = {}) {
    return new Promise((resolvePromise, reject) => {
        const child = spawn(command, args, { stdio: ['ignore', 'inherit', 'inherit'], ...options });
        child.once('error', reject);
        child.once('close', code => {
            if (code === 0) {
                resolvePromise();
            } else {
                reject(new Error(`${command} exited with code ${code}.`));
            }
        });
    });
}

async function extractArchive(archivePath, destination) {
    await fs.mkdir(destination, { recursive: true });
    try {
        await run('tar', ['--zstd', '-xf', archivePath, '-C', destination]);
        return;
    } catch (tarError) {
        console.warn(`tar --zstd extraction failed (${tarError.message}); trying zstd fallback.`);
    }

    await new Promise((resolvePromise, reject) => {
        const zstd = spawn('zstd', ['--decompress', '--stdout', archivePath], { stdio: ['ignore', 'pipe', 'inherit'] });
        const tar = spawn('tar', ['-xf', '-', '-C', destination], { stdio: ['pipe', 'inherit', 'inherit'] });
        let zstdError;
        let tarError;
        zstd.once('error', error => { zstdError = error; });
        tar.once('error', error => { tarError = error; });
        zstd.stdout.pipe(tar.stdin);
        let zstdCode;
        let tarCode;
        const complete = () => {
            if (zstdCode === undefined || tarCode === undefined) {
                return;
            }
            if (zstdError || tarError || zstdCode !== 0 || tarCode !== 0) {
                reject(new Error('Unable to extract the .tar.zst archive. Install tar with --zstd support or install zstd and ensure it is on PATH.'));
            } else {
                resolvePromise();
            }
        };
        zstd.once('close', code => { zstdCode = code; complete(); });
        tar.once('close', code => { tarCode = code; complete(); });
    });
}

async function findJsonDocuments(directory) {
    const documents = [];
    async function visit(currentDirectory) {
        const entries = await fs.readdir(currentDirectory, { withFileTypes: true });
        for (const entry of entries) {
            const entryPath = join(currentDirectory, entry.name);
            if (entry.isDirectory()) {
                await visit(entryPath);
            } else if (entry.isFile() && entry.name.endsWith('.json')) {
                documents.push(entryPath);
            }
        }
    }
    await visit(directory);
    return documents.sort((left, right) => left.localeCompare(right));
}

function parseCsv(content) {
    const rows = [];
    let row = [];
    let value = '';
    let quoted = false;
    for (let index = 0; index < content.length; index += 1) {
        const character = content[index];
        if (quoted) {
            if (character === '"' && content[index + 1] === '"') {
                value += '"';
                index += 1;
            } else if (character === '"') {
                quoted = false;
            } else {
                value += character;
            }
        } else if (character === '"') {
            quoted = true;
        } else if (character === ',') {
            row.push(value);
            value = '';
        } else if (character === '\n') {
            row.push(value.replace(/\r$/, ''));
            rows.push(row);
            row = [];
            value = '';
        } else {
            value += character;
        }
    }
    if (value || row.length > 0) {
        row.push(value.replace(/\r$/, ''));
        rows.push(row);
    }
    return rows;
}

function csvValue(value) {
    return `"${String(value).replaceAll('"', '""')}"`;
}

async function latestResultsByPath(stateFile) {
    if (!existsSync(stateFile)) {
        return new Map();
    }
    const rows = parseCsv(await fs.readFile(stateFile, 'utf8'));
    if (rows.length === 0) {
        return new Map();
    }
    const columnIndexes = Object.fromEntries(rows[0].map((column, index) => [column, index]));
    if (columnIndexes.Path === undefined || columnIndexes.Status === undefined) {
        throw new Error(`State file has no Path and Status columns: ${stateFile}`);
    }
    return new Map(rows.slice(1)
        .filter(row => row[columnIndexes.Path] && row[columnIndexes.Status])
        .map(row => [row[columnIndexes.Path], row[columnIndexes.Status]]));
}

async function appendResult(stateFile, result) {
    const header = 'Timestamp,Path,Status,HttpStatus,DurationMs,Message\n';
    if (!existsSync(stateFile) || (await fs.stat(stateFile)).size === 0) {
        await fs.mkdir(dirname(stateFile), { recursive: true });
        await fs.appendFile(stateFile, header, 'utf8');
    }
    const row = [result.timestamp, result.path, result.status, result.httpStatus, result.durationMs, result.message]
        .map(csvValue)
        .join(',');
    await fs.appendFile(stateFile, `${row}\n`, 'utf8');
}

async function getAccessToken(options) {
    const tokenUrl = `${options.keycloakBaseUrl.replace(/\/$/, '')}/realms/${encodeURIComponent(options.realm)}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
        grant_type: 'password',
        client_id: options.clientId,
        client_secret: options.clientSecret,
        username: options.username,
        password: options.password,
        scope: 'openid'
    });
    const response = await fetchOrThrow(tokenUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body
    }, options.timeoutSeconds);
    const tokenResponse = await response.json();
    if (!tokenResponse.access_token || typeof tokenResponse.access_token !== 'string') {
        throw new Error(`Keycloak did not return an access token from ${tokenUrl}.`);
    }
    const expiresInSeconds = Number(tokenResponse.expires_in);
    return {
        value: tokenResponse.access_token,
        expiresInSeconds: Number.isFinite(expiresInSeconds) && expiresInSeconds > 0
            ? expiresInSeconds
            : 0
    };
}

function createAccessTokenProvider(options) {
    const refreshBeforeExpiryMilliseconds = 60 * 1000;
    let token;
    let expiresAt = 0;
    return {
        async get(forceRefresh = false) {
            if (!forceRefresh && token && Date.now() < expiresAt - refreshBeforeExpiryMilliseconds) {
                return token;
            }
            const tokenResponse = await getAccessToken(options);
            token = tokenResponse.value;
            expiresAt = tokenResponse.expiresInSeconds > 0
                ? Date.now() + tokenResponse.expiresInSeconds * 1000
                : Number.POSITIVE_INFINITY;
            return token;
        }
    };
}

async function authenticatedFetchOrThrow(url, requestOptions, accessTokenProvider, options) {
    for (let attempt = 0; attempt < 2; attempt += 1) {
        const accessToken = await accessTokenProvider.get(attempt === 1);
        try {
            return await fetchOrThrow(url, {
                ...requestOptions,
                headers: { ...requestOptions.headers, Authorization: `Bearer ${accessToken}` }
            }, options.timeoutSeconds);
        } catch (error) {
            if (attempt === 0 && error?.httpStatus === 401) {
                console.warn('Received HTTP 401 from the backend; refreshing the Keycloak access token and retrying once.');
                continue;
            }
            throw error;
        }
    }
    throw new Error('Authenticated request retry unexpectedly completed without a response.');
}

async function importDocument(csaf, relativePath, accessTokenProvider, options) {
    const summary = `Imported Red Hat advisory ${documentIdentifier(csaf, relativePath)}.`;
    const response = await authenticatedFetchOrThrow(`${options.apiBaseUrl.replace(/\/$/, '')}/advisories`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ summary, csaf })
    }, accessTokenProvider, options);
    return response.status;
}

async function advisoryExistsForTrackingId(trackingId, accessTokenProvider, options) {
    if (typeof trackingId !== 'string' || !trackingId.trim()) {
        return false;
    }
    const expression = JSON.stringify({
        type: 'Operator',
        selector: ['csaf', 'document', 'tracking', 'id'],
        operatorType: 'Equal',
        value: trackingId,
        valueType: 'Text'
    });
    const url = `${options.apiBaseUrl.replace(/\/$/, '')}/advisories?expression=${encodeURIComponent(expression)}`;
    const response = await authenticatedFetchOrThrow(url, {}, accessTokenProvider, options);
    const advisories = await response.json();
    if (!Array.isArray(advisories)) {
        throw new Error(`Unexpected advisory list response while recovering ${trackingId}.`);
    }
    return advisories.some(advisory => advisory?.documentTrackingId === trackingId);
}

function documentIdentifier(csaf, relativePath) {
    const trackingId = csaf?.document?.tracking?.id;
    return typeof trackingId === 'string' && trackingId.trim() ? trackingId : relativePath;
}

async function main() {
    const options = parseArguments(process.argv.slice(2));
    const archiveName = `csaf_advisories_${options.archiveDate}.tar.zst`;
    const archivePath = join(WORK_DIRECTORY, archiveName);
    const checksumPath = `${archivePath}.sha256`;
    const dataDirectory = options.dataDirectory || join(WORK_DIRECTORY, `extracted-${options.archiveDate}`);
    const stateFile = options.stateFile || join(WORK_DIRECTORY, `import-results-${options.archiveDate}.csv`);

    await fs.mkdir(WORK_DIRECTORY, { recursive: true });
    if (!options.skipDownload) {
        const archiveUrl = `${PROVIDER_BASE_URL}/${archiveName}`;
        if (existsSync(archivePath)) {
            console.log(`Using existing archive: ${archivePath}`);
        } else {
            console.log(`Downloading ${archiveUrl}`);
            await downloadFile(archiveUrl, archivePath, options.timeoutSeconds);
        }
        await downloadFile(`${archiveUrl}.sha256`, checksumPath, options.timeoutSeconds);
        await verifyChecksum(archivePath, checksumPath);
        if (!existsSync(dataDirectory)) {
            console.log(`Extracting ${archiveName}`);
            await extractArchive(archivePath, dataDirectory);
        }
    }

    if (!existsSync(dataDirectory)) {
        throw new Error(`Data directory not found: ${dataDirectory}. Remove --skip-download or provide --data-directory.`);
    }
    let documents = await findJsonDocuments(dataDirectory);
    if (documents.length === 0) {
        throw new Error(`No JSON advisory documents found under ${dataDirectory}.`);
    }
    if (options.limit > 0) {
        documents = documents.slice(0, options.limit);
    }

    const previousResults = options.resume ? await latestResultsByPath(stateFile) : new Map();
    const accessTokenProvider = createAccessTokenProvider(options);
    let success = 0;
    let failed = 0;
    let skipped = 0;
    console.log(`Importing ${documents.length} Red Hat CSAF documents into ${options.apiBaseUrl}`);

    for (let index = 0; index < documents.length; index += 1) {
        const documentPath = documents[index];
        const relativePath = relative(dataDirectory, documentPath).split(sep).join('/');
        const previousStatus = previousResults.get(relativePath);
        if (options.resume && previousStatus === 'Success') {
            skipped += 1;
            continue;
        }
        process.stdout.write(`[${index + 1}/${documents.length}] ${relativePath}\n`);
        const started = Date.now();
        try {
            const csaf = JSON.parse(await fs.readFile(documentPath, 'utf8'));
            const trackingId = csaf?.document?.tracking?.id;
            if (options.resume && previousStatus === 'Started'
                && await advisoryExistsForTrackingId(trackingId, accessTokenProvider, options)) {
                await appendResult(stateFile, {
                    timestamp: new Date().toISOString(), path: relativePath, status: 'Success',
                    httpStatus: 200, durationMs: Date.now() - started,
                    message: 'Recovered: advisory already exists for the CSAF tracking ID.'
                });
                success += 1;
                continue;
            }
            await appendResult(stateFile, {
                timestamp: new Date().toISOString(), path: relativePath, status: 'Started',
                httpStatus: '', durationMs: '', message: ''
            });
            const httpStatus = await importDocument(csaf, relativePath, accessTokenProvider, options);
            await appendResult(stateFile, {
                timestamp: new Date().toISOString(), path: relativePath, status: 'Success',
                httpStatus, durationMs: Date.now() - started, message: ''
            });
            success += 1;
        } catch (error) {
            const message = error instanceof Error ? error.message.replaceAll(/[\r\n]+/g, ' ') : String(error);
            await appendResult(stateFile, {
                timestamp: new Date().toISOString(), path: relativePath, status: 'Failed',
                httpStatus: extractHttpStatus(message), durationMs: Date.now() - started, message
            });
            console.warn(`Failed to import ${relativePath}: ${message}`);
            failed += 1;
        }
    }
    console.log(`Completed. Success: ${success}; Failed: ${failed}; Skipped: ${skipped}; Results: ${stateFile}`);
    if (failed > 0) {
        process.exitCode = 1;
    }
}

function extractHttpStatus(message) {
    const match = /^HTTP (\d{3})\b/.exec(message);
    return match ? Number(match[1]) : '';
}

main().catch(error => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
});