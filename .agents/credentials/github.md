# Credentials & Security

All GitHub authentication — both publishing to and resolving from GitHub
Packages — uses exactly two environment variables provided by the GitHub
Actions runner context:

### Username
```groovy
USERNAME = System.getenv('GITHUB_ACTOR')
```

### Password / Token
```groovy
PASSWORD = System.getenv('GITHUB_TOKEN')
```

Do NOT add any additional fallbacks (such as `AGENT_GITHUB_*` or
`USER_GITHUB_*`). Only `GITHUB_ACTOR` and `GITHUB_TOKEN` are used.
