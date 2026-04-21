#!/usr/bin/env node
// Evaluate a FHIRPath expression against a resource using the official
// fhirpath.js reference implementation. See SKILL.md for usage.

const fs = require('fs');
const path = require('path');

function locateFhirpath() {
  const fromSkill = path.resolve(__dirname, '../../../..', '.local/fhirpath.js');
  const fromCwd = path.resolve(process.cwd(), '.local/fhirpath.js');
  for (const c of [fromSkill, fromCwd]) {
    if (fs.existsSync(path.join(c, 'src/fhirpath.js'))) return c;
  }
  throw new Error(
    'Could not locate fhirpath.js. Expected a symlink or checkout at .local/fhirpath.js ' +
    'relative to the project root. If the symlink exists, run `npm install` inside its target.'
  );
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--expr') out.expr = argv[++i];
    else if (a === '--resource') out.resource = argv[++i];
    else if (a === '--env') out.env = argv[++i];
    else if (a === '--model') out.model = argv[++i];
    else if (a === '-h' || a === '--help') out.help = true;
    else throw new Error(`Unknown argument: ${a}`);
  }
  return out;
}

function usage() {
  return [
    'Usage: fp-eval.js --expr <expression> [--resource <path-or-json>] [--env <json>] [--model r4|stu3|r5]',
    '',
    '  --expr      FHIRPath expression (required).',
    '  --resource  Path to a JSON file OR inline JSON. Defaults to {} if omitted.',
    '  --env       JSON object of environment variables (keys without %). e.g. \'{"myvar": 42}\'',
    '  --model     FHIR model: r4, stu3, or r5. Enables FHIR type awareness.',
  ].join('\n');
}

function loadResource(arg) {
  if (arg === undefined) return {};
  const trimmed = arg.trim();
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) return JSON.parse(trimmed);
  return JSON.parse(fs.readFileSync(trimmed, 'utf8'));
}

function main() {
  let args;
  try {
    args = parseArgs(process.argv.slice(2));
  } catch (e) {
    console.error(e.message + '\n' + usage());
    process.exit(2);
  }
  if (args.help || !args.expr) {
    console.error(usage());
    process.exit(args.help ? 0 : 2);
  }

  const fhirpathRoot = locateFhirpath();
  const fhirpath = require(path.join(fhirpathRoot, 'src/fhirpath'));
  const model = args.model
    ? require(path.join(fhirpathRoot, 'fhir-context', args.model))
    : undefined;

  let resource, env;
  try {
    resource = loadResource(args.resource);
  } catch (e) {
    console.error('Failed to load --resource: ' + e.message);
    process.exit(2);
  }
  try {
    env = args.env ? JSON.parse(args.env) : undefined;
  } catch (e) {
    console.error('Failed to parse --env JSON: ' + e.message);
    process.exit(2);
  }

  let result;
  try {
    result = fhirpath.evaluate(resource, args.expr, env, model);
  } catch (e) {
    console.error('Evaluation error: ' + e.message);
    process.exit(1);
  }

  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
}

main();
