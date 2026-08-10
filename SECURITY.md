# Security policy

We take the security of this project seriously. This document explains how to
report a vulnerability and what to expect once you have done so.

## Reporting a vulnerability

Please report security vulnerabilities privately. Do not open a public issue,
pull request, or discussion for a suspected vulnerability, as this can put
other users at risk before a fix is available.

Use GitHub's [private vulnerability reporting](https://github.com/aehrc/fp2spark/security/advisories/new)
to open a private security advisory. This keeps the report, our responses, and
any draft fix in one place.

Please include as much of the following as you can, so we can reproduce and
assess the issue quickly:

- A description of the vulnerability and the impact you believe it has.
- Step-by-step instructions to reproduce the issue, including any FHIRPath
  expressions, FHIR resources, or configuration required.
- Any proof-of-concept code, logs, or screenshots.
- Your assessment of severity, if you have one.

## What to expect

When you report a vulnerability, you can expect the following:

- We will acknowledge your report within five business days.
- We will work with you to confirm the issue and determine its severity and
  scope.
- We will keep you informed of our progress towards a fix.
- Once a fix is released, we will publish a security advisory and credit you
  for the discovery, unless you ask to remain anonymous.

We ask that you give us a reasonable opportunity to release a fix before
disclosing the vulnerability publicly. We are happy to coordinate the timing
of public disclosure with you.

## Scope

This policy covers the code maintained in this repository.

Vulnerabilities in third-party dependencies are best reported to the relevant
upstream project. If a dependency vulnerability affects this project and
requires a change on our side, please let us know so that we can address it.

This project is experimental software and is provided without warranty under
the [Apache License, version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
