# Languages and build technologies

GitHub's **Languages** bar is generated automatically from the files committed to the repository. It is not configured as a property of a Git commit. GitHub uses Linguist to classify files and updates repository language statistics after changes are pushed to the default branch. The result can take a little time to refresh.

## Languages used by Anniehilation

| Language / format | Role | Typical files |
|---|---|---|
| Java | Android activities, services, custom views, game logic | `*.java` |
| XML | Android manifest, layouts, drawable and value resources | `*.xml` |
| Kotlin DSL | Gradle project and module build scripts | `*.gradle.kts` |
| TOML | Gradle dependency/version catalog | `*.toml` |
| Markdown | Documentation | `*.md` |
| Batch | Optional Windows developer/maintenance scripts, only if committed | `*.bat`, `*.cmd` |
| PowerShell | Optional Windows automation scripts, only if committed | `*.ps1` |

The primary application languages are **Java and XML**. Kotlin DSL and TOML are build-configuration formats; their presence does not mean the Android application itself is written in Kotlin.

## Do not try to add a manual language list to a commit

To update the language breakdown:

1. Commit the real source files (Java, XML, Gradle scripts, and so on).
2. Push the commit to the repository's default branch.
3. Allow GitHub to recalculate the Languages bar.

Do not add fake source files or rename assets just to influence the percentages. Use `.gitattributes` only when a real file needs a justified classification override or should be excluded as generated/vendor content.

## Reference

- [GitHub Docs: About repository languages](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-repository-languages)
- [GitHub Linguist: How Linguist works](https://github.com/github-linguist/linguist/blob/main/docs/how-linguist-works.md)
