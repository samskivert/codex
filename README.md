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

## Running Codex

You can download an installer for Codex here:

  * Mac: [Codex-1.0.dmg](http://samskivert.com/codex/Codex-1.0.dmg)
  * Linux: TODO
  * Windows: TODO

Codex is a standalone application which is run via [Getdown], an auto-updating Java app launcher.
When Codex is running, you should see a C in your system tray which you can use to quit Codex and
to easily open a web browser viewing Codex's known-projects page.

Normally you don't interact with Codex via the web interface, rather via editor integration.
However, you can do documentation searches via the web interface, which can be useful if you're not
currently editing a file in a particular project and want to look up documentation in that project
or its dependencies.

## Editor Integration

Codex comes with Emacs integration out of the box, just add the following to `.emacs`:

    (add-to-list 'load-path "~/projects/codex/src/main/elisp")
    (autoload 'codex-mode "codex-mode" "Minor mode for augmenting coding modes." t)
    (mapc (lambda (lang-hook) (add-hook lang-hook 'codex-mode))
          '(java-mode-hook
            scala-mode-hook
            actionscript-mode-hook
            csharp-mode-hook))

Where `CODEXDIR/elisp` depends on your platform:

  * Mac: `~/Library/Application Support/Codex/elisp`
  * Linux: TODO
  * Windows: TODO

Use `M-x describe-minor-mode codex-mode` to view the default key mappings, and customize to taste.

## Status

Codex currently does a passable job of grokking Java, Scala, C# and ActionScript code. It
understands projects managed by Maven and SBT. It can locate and serve up documentation from Maven
and Ivy repositories, and auto-download missing docs from Maven Central.

[Getdown]: https://code.google.com/p/getdown/
