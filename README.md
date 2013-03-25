# Codex

Codex is a code indexing and query engine that aims to provide IDE-like functionality without the
I. It is designed to be integrated into the editor of your choice and to process projects based on
existing build systems (unlike IDEs which tend to take over your project's build). It is written in
Scala and thus has a bias toward Java-like languages, but it is designed to support any language.

## Architecture

Codex runs as a daemon on your workstation and interfaces with your editor via simple HTTP requests
and responses. It contains pluggable mechanisms for extracting metadata for a project (most
importantly: where is the source code and what are the external dependencies), as well as pluggable
mechanisms for extracting information from source code.

## Capabilities

Codex builds indexes of the code in your projects and allows you to issue queries like "where is
the function named foo defined?" Codex is designed to scale from modest knowledge of your code
(e.g. "these files define these modules and these modules contain these functions", or even just
"these files define these functions") to more complete knowledge of your project (e.g. the complete
signature of every definition in your code, its kind, and the precise definition to which every
name refers).

Codex makes use of your project dependencies to narrow the scope of your queries and precise type
information (if available) to deliver relevant results.

Codex aims to extract and make available the inline documentation from your code as well.
Documentation is occasionally sufficiently relevant and correct to be useful.

## Status

Codex is just getting started, though I am stealing and repurposing code from numerous related
projects that I've written over the years, so it should hopefully start delivering value quickly.
