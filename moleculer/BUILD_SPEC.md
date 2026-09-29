# Moleculer.js Microservices POC: Build Specification

This document describes a working application completely enough to rebuild it. Every code block was run and checked (section 12) before it was written here.

---

## 0. Instructions for Claude Code

Read the whole document before writing anything. Then:

1. **Reproduce this application exactly.** Do not improve it, refactor it, rename anything or add features. Section 13 lists known weaknesses that must be kept as they are. Improvements can be suggested afterwards, in a separate list.
2. **Use the pinned versions in section 5.** Do not install "latest". Newer Moleculer (0.15) and moleculer-web (0.11) have different APIs and break this code.
3. **Read section 7 (traps) before starting.** Each item is a bug that was already hit once. Every one of them looks harmless in code.
4. **Build the two services in the order in section 9**, and run the checks in section 12 before saying you are finished.
5. **You cannot create the PostgreSQL database or the real Eureka server.** Ask the user for them, or use the tools in section 10 for testing.
6. If anything here contradicts code that already exists in the repo, **the repo wins**. Tell the user about the difference.

---

## 1. What the application is

Two independent Node.js microservices built with Moleculer, plus calls to a Java service that already exists. All discovery goes through one Eureka server, which the Java services also use.

```
                       +---------------------------+
                       |   Eureka server :8761     |
                       |  (Spring Boot, existing)  |
                       +---------------------------+
                          ^          ^           ^
              registers   |          | registers | registers
                          |          |           |
  Client ----> CONSUMER-SERVICE ---------> USER-PROFILE-SERVICE ----> PostgreSQL
  (Postman)      :3002        axios call       :3001                  userprofile_db
                    |
                    +---------- axios call ----> CUSTOMER-SERVICE (Java, existing, :8081)
```

| Service | Port | Eureka name | Owns |
|---|---|---|---|
| `UserProfileServiceNode` | 3001 | `USER-PROFILE-SERVICE` | The `user_profile` table in PostgreSQL |
| `ConsumerServiceNode` | 3002 | `CONSUMER-SERVICE` | No data. It looks services up in Eureka and calls them |
| Java `customer-service` | 8081 | `CUSTOMER-SERVICE` | Already exists. **Do not build it** |

Features, all of which must work:

- A fixed-response endpoint, and a health endpoint used by Eureka.
- PostgreSQL storage with a username and password (through Sequelize).
- Request validation that returns a **custom 400** (`BadRequestError`), not Moleculer's default 422.
- Registration with Eureka on start-up and de-registration on Ctrl+C.
- Service discovery: the Consumer finds other services by name in a cached copy of the Eureka registry.
- A correlation ID (`X-Correlation-Id` header): if the caller sends one it is passed on unchanged, otherwise the Consumer generates a UUID.
- Node calling Node, and Node calling Java, both with axios.

---

## 2. Concepts used in the code

| Spring Boot | Moleculer (this project) |
|---|---|
| `@SpringBootApplication` main class | `index.js`, which creates and starts the `ServiceBroker` |
| `@RestController` | A `*.service.js` file |
| `@GetMapping` / `@PostMapping` | An entry under `actions`, with `rest: "GET /path"` |
| `@RequestMapping("/v1/x")` | `settings.rest` in the service |
| Bean validation annotations | The `params` block (fastest-validator, bundled with Moleculer) |
| Service class | `*.handler.js` |
| `@ControllerAdvice` | The broker's `errorHandler` option in `index.js` |
| Embedded Tomcat | `moleculer-web`, added as a mixin in `*.gateway.js` |
| Hibernate / JPA entity | Sequelize model in `model/` |
| `application.yml` | `config.json` |
| `RestTemplate` / Feign | axios |

`ctx` is the context object Moleculer creates for each request. `ctx.params` holds the body and path parameters. `ctx.meta` holds extra data such as values copied from headers.

---

## 3. Prerequisites

- **Node.js 18 or newer.** It was developed on Node 22 and also runs on Node 25.
- **PostgreSQL** on `localhost:5432`, username `postgres`, password `postgres`, with an **empty database named `userprofile_db`** already created:
  ```sql
  CREATE DATABASE userprofile_db;
  ```
  The application creates the table itself. It does not create the database.
- **A Eureka server** at `http://localhost:8761/eureka/`. This is an existing Spring Boot discovery server. For testing without it, use `tools/mock-eureka` (section 10).
- **A Java service** registered in Eureka as `CUSTOMER-SERVICE` that answers `GET /api/customers/{id}`. It already exists in the real project. For testing without it, use `tools/fake-customer-service.js` (section 10).

---

## 4. Ground rules for the code

- CommonJS (`require`), `"type": "commonjs"`. No TypeScript, no ES modules.
- 2-space indentation and double quotes.
- Do not add packages that are not listed in section 5.
- Do not add a build step, a test framework or a linter.
- Keep file and folder names exactly as shown. The layout mirrors the team's other Moleculer projects.

---

## 5. Pinned versions

Install with exactly these commands, from inside each service folder (next to its `package.json`).

**UserProfileServiceNode**

```bash
npm install moleculer@0.14.36 moleculer-web@0.10.8 winston@3.19.0 sequelize@6.37.8 eureka-js-client@4.5.0 pg pg-hstore
```

**ConsumerServiceNode**

```bash
npm install moleculer@0.14.36 moleculer-web@0.10.8 winston@3.19.0 eureka-js-client@4.5.0 axios@1.20.0
```

Resolved versions used when the checks in section 12 were run:

| Package | Version | Used by |
|---|---|---|
| moleculer | 0.14.36 | both |
| moleculer-web | 0.10.8 | both |
| winston | 3.19.0 | both |
| eureka-js-client | 4.5.0 | both |
| sequelize | 6.37.8 | UserProfile |
| pg | 8.x (8.23.0 when checked) | UserProfile |
| pg-hstore | 2.3.4 | UserProfile |
| axios | 1.20.0 | Consumer |

`fastest-validator` is not installed separately. Moleculer brings it in.

---

## 6. Folder layout

```
UserProfileServiceNode/
├── package.json
├── .gitignore
└── src/
    ├── errors/
    │   └── index.js
    └── UserProfileServiceNode/
        ├── model/
        │   └── UserProfile.model.js
        ├── config.json
        ├── eureka.client.js
        ├── index.js
        ├── userprofileservice.db.config.js
        ├── UserProfileServiceNode.gateway.js
        ├── UserProfileServiceNode.handler.js
        ├── UserProfileServiceNode.service.js
        └── winston.logger.service.js

ConsumerServiceNode/
├── package.json
├── .gitignore
└── src/
    ├── errors/
    │   └── index.js
    └── ConsumerServiceNode/
        ├── config.json
        ├── eureka.client.js
        ├── index.js
        ├── winston.logger.service.js
        ├── ConsumerServiceNode.gateway.js
        ├── ConsumerServiceNode.handler.js
        └── ConsumerServiceNode.service.js

tools/                       (optional, for testing without the real servers)
├── mock-eureka/
│   ├── package.json
│   └── server.js
└── fake-customer-service.js
```

The two services are separate projects. Each has its own `package.json` and `node_modules`. Neither imports code from the other. The only link between them at run time is the name in Eureka.

---

## 7. Traps: mistakes already made once

Every item here caused a real failure with little or no error message. Check each one while building.

1. **Service `version` must be a number** (`version: 1`), not a string. Moleculer's full service name becomes `v1.userProfileService`, which the gateway whitelist matches. Also, when `settings.rest` is set by hand it is used exactly as written, so the `/v1` must be written into it: `rest: "/v1/userprofile"`.
2. **Destructure named exports.** `eureka.client.js` exports an object, so import it as `const { startEureka, stopEureka } = require("./eureka.client");`. Without the braces the variable holds the whole object and calling it fails with `startEureka is not a function`.
3. **Do not redeclare `client`.** In `eureka.client.js`, `let client = null;` is declared at module level. Inside `startEureka` it must be assigned with `client = new Eureka({...})`, **not** `const client = ...`. A `const` there creates a separate local variable, the module-level one stays `null`, and `stopEureka()` silently does nothing.
4. **`skipProcessEventRegistration: true` on the broker.** Moleculer installs its own SIGINT handler that stops the broker and exits. That exit happens before your own handler finishes de-registering from Eureka. With this option set, only your handler runs.
5. **Startup order.** `await broker.start()` first, then `await startEureka()`. Eureka checks the health URL, which only exists once the HTTP server is up.
6. **The Consumer needs `fetchRegistry: true`.** Without it `getInstancesByAppId` always returns nothing. The lookup reads a local cached copy of the registry, refreshed about every 30 seconds. It does not call Eureka on every request. A Consumer that starts before the target service registers will not see it until the next refresh, so start the Consumer last.
7. **Each service's `healthCheckUrl` uses its own URL prefix.** UserProfile: `/v1/userprofile/health`. Consumer: `/v1/consumer/health`. Copying one file to make the other and forgetting this leaves Eureka checking a URL that does not exist.
8. **Node lower-cases header names.** Read `req.headers["x-correlation-id"]`, even though the header is sent as `X-Correlation-Id`.
9. **The Eureka `port` value has an odd shape**: `{ $: port, "@enabled": true }`. When Eureka returns an instance, the number is at `instance.port["$"]`.
10. **`sync()` creates a missing table but never alters an existing one.** If a column is renamed or changed in the model, the old table stays as it was, and requests appear to succeed while data for the new column is dropped. During development, drop the table and let it be recreated: `DROP TABLE user_profile;`. In PostgreSQL the columns are mixed case (`"userId"`, `"firstName"`), so SQL must quote them.
11. **`allowNull: false` does not reject empty strings.** Only a missing or null value. The `params` block with `min: 1` is what rejects `""`.
12. **Each service needs a unique `nodeID`** (`user-profile-node`, `consumer-node`) and its own port.
13. **`config.javaService.path` already starts with `/`.** Build the URL as `${baseUrl}${config.javaService.path}/${id}` with no extra slash, or the request goes to `//api/customers/1`. The real Java path is `/api/customers`.
14. **`getInstancesByAppId("")` throws** `Unable to query instances with no appId`. If it appears, `config.javaService.appName` is missing or mistyped. The key must be exactly `appName`.
15. **Windows and `npm start`.** Ctrl+C under `npm start` shows `Terminate batch job (Y/N)?`, which can stop the process before de-registration runs. Start the services with `node src/<Service>/index.js` directly.
16. **Restart after every code or config change.** Moleculer does not hot-reload, and `config.json` is read once at start-up.

---

## 8. Endpoints

**UserProfileServiceNode (port 3001)**

| Method | URL | Purpose |
|---|---|---|
| GET | `/v1/userprofile/hello` | Fixed JSON response |
| GET | `/v1/userprofile/health` | `{"status":"UP"}`, used by Eureka |
| GET | `/v1/userprofile/profiles` | All profiles |
| GET | `/v1/userprofile/profiles/:userId` | One profile by `userId` |
| POST | `/v1/userprofile/profiles` | Create a profile. Body: `userId`, `firstName`, `lastName`, `email` |

**ConsumerServiceNode (port 3002)**

| Method | URL | Purpose |
|---|---|---|
| GET | `/v1/consumer/health` | `{"status":"UP"}`, used by Eureka |
| GET | `/v1/consumer/user-profile/:userId` | Looks up USER-PROFILE-SERVICE in Eureka and calls it |
| GET | `/v1/consumer/java-customer/:id` | Looks up CUSTOMER-SERVICE in Eureka and calls `GET /api/customers/{id}` |

Validation rules: `userId` 1 to 20 characters; `firstName` and `lastName` 1 to 50 characters; `email` must be a valid e-mail; Consumer `userId` and `id` path parameters 1 to 20 characters. A failure returns **400** with `name: "BadRequestError"`, `type: "BAD_REQUEST"` and a `data` list of `{ field, message }`.

---

## 9. Build order

Build and check each step before starting the next. Commit after each one if the user wants phases.

1. **UserProfileServiceNode**: `package.json`, install packages, `src/errors`, `config.json`, logger, DB config, model, handler, service, gateway, `eureka.client.js`, `index.js`.
2. **ConsumerServiceNode**: same order, without a database.
3. Run the checks in section 12.

The complete code follows. Copy every file exactly.

---

## 9A. UserProfileServiceNode


### `UserProfileServiceNode/package.json`

Project manifest. Dependencies are added by the install command in section 5; the block below shows the final result.

```json
{
  "name": "userprofileservicenode",
  "version": "1.0.0",
  "description": "Moleculer POC - User Profile microservice",
  "main": "src/UserProfileServiceNode/index.js",
  "scripts": {
    "start": "node src/UserProfileServiceNode/index.js"
  },
  "license": "ISC",
  "type": "commonjs",
  "dependencies": {
    "eureka-js-client": "^4.5.0",
    "moleculer": "^0.14.36",
    "moleculer-web": "^0.10.8",
    "pg": "^8.23.0",
    "pg-hstore": "^2.3.4",
    "sequelize": "^6.37.8",
    "winston": "^3.19.0"
  }
}
```

### `UserProfileServiceNode/.gitignore`

```
node_modules
*.sqlite
logs
```

### `UserProfileServiceNode/src/errors/index.js`

Custom 400 exception.

```javascript
const { Errors } = require("moleculer");

class BadRequestError extends Errors.MoleculerError {
  constructor(message, details) {
    super(message, 400, "BAD_REQUEST", details);
    this.name = "BadRequestError";
  }
}

module.exports = { BadRequestError };
```

### `UserProfileServiceNode/src/UserProfileServiceNode/config.json`

All environment settings. The database password here is for the local POC only.

```json
{
  "app": {
    "name": "USER-PROFILE-SERVICE",
    "host": "localhost",
    "port": 3001
  },
  "moleculer": {
    "namespace": "poc",
    "logLevel": "info"
  },
  "db": {
    "dialect": "postgres",
    "host": "localhost",
    "port": 5432,
    "database": "userprofile_db",
    "username": "postgres",
    "password": "postgres"
  },
  "eureka": {
    "host": "localhost",
    "port": 8761,
    "servicePath": "/eureka/apps/"
  }
}
```

### `UserProfileServiceNode/src/UserProfileServiceNode/winston.logger.service.js`

One shared logger, exported as an object.

```javascript
const winston = require("winston");

module.exports = winston.createLogger({
  level: "info",
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.printf(({ timestamp, level, message }) => `${timestamp} [${level}] ${message}`)
  ),
  transports: [new winston.transports.Console()],
});
```

### `UserProfileServiceNode/src/UserProfileServiceNode/userprofileservice.db.config.js`

Sequelize connection, created once and reused.

```javascript
const { Sequelize } = require("sequelize");
const config = require("./config.json");
const logger = require("./winston.logger.service");

let sequelize = null;

async function getSequelize() {
  if (sequelize) return sequelize;

  const { dialect, host, port, database, username, password } = config.db;

  sequelize = new Sequelize(database, username, password, {
    host,
    port,
    dialect,
    logging: false,
  });

  await sequelize.authenticate();
  logger.info(`DB connected: ${dialect} database '${database}' on ${host}:${port} as '${username}'`);
  return sequelize;
}

module.exports = { getSequelize };
```

### `UserProfileServiceNode/src/UserProfileServiceNode/model/UserProfile.model.js`

The `user_profile` table definition.

```javascript
const { DataTypes } = require("sequelize");
const { getSequelize } = require("../userprofileservice.db.config");

let UserProfile = null;

async function initUserProfileModel() {
  if (UserProfile) return UserProfile;

  const sequelize = await getSequelize();

  UserProfile = sequelize.define(
    "UserProfile",
    {
      id: { type: DataTypes.INTEGER, primaryKey: true, autoIncrement: true },
      userId: { type: DataTypes.STRING, allowNull: false, unique: true },
      firstName: { type: DataTypes.STRING, allowNull: false },
      lastName: { type: DataTypes.STRING, allowNull: false },
      email: { type: DataTypes.STRING, allowNull: false },
    },
    { tableName: "user_profile", timestamps: true }
  );

  await UserProfile.sync();

  return UserProfile;
}

module.exports = { initUserProfileModel };
```

### `UserProfileServiceNode/src/UserProfileServiceNode/UserProfileServiceNode.handler.js`

Business logic. Plain async functions that receive `ctx`.

```javascript
const { initUserProfileModel } = require("./model/UserProfile.model");

const hello = async () => {
  return {
    message: "Hello from UserProfileServiceNode",
    service: "USER-PROFILE-SERVICE",
    time: new Date().toISOString(),
  };
};

const health = async () => {
  return { status: "UP" };
};

const getAllProfiles = async () => {
  const UserProfile = await initUserProfileModel();
  return UserProfile.findAll();
};

const getProfile = async (ctx) => {
  const UserProfile = await initUserProfileModel();
  const profile = await UserProfile.findOne({ where: { userId: ctx.params.userId } });
  if (!profile) {
    throw new Error(`No profile found for userId ${ctx.params.userId}`);
  }
  return profile;
};

const createProfile = async (ctx) => {
  const UserProfile = await initUserProfileModel();
  return UserProfile.create(ctx.params);
};

module.exports = { hello, health, getAllProfiles, getProfile, createProfile };
```

### `UserProfileServiceNode/src/UserProfileServiceNode/UserProfileServiceNode.service.js`

URLs, parameter validation and the link from each URL to a handler function.

```javascript
const Handler = require("./UserProfileServiceNode.handler");

module.exports = {
  name: "userProfileService",
  version: 1,
  settings: {
    rest: "/v1/userprofile",
  },

  actions: {
    hello: {
      rest: "GET /hello",
      handler: (ctx) => Handler.hello(ctx),
    },

    health: {
      rest: "GET /health",
      handler: (ctx) => Handler.health(ctx),
    },

    getAllProfiles: {
      rest: "GET /profiles",
      handler: (ctx) => Handler.getAllProfiles(ctx),
    },

    getProfile: {
      rest: "GET /profiles/:userId",
      handler: (ctx) => Handler.getProfile(ctx),
    },

    createProfile: {
      rest: "POST /profiles",
      params: {
        userId: { type: "string", min: 1, max: 20 },
        firstName: { type: "string", min: 1, max: 50 },
        lastName: { type: "string", min: 1, max: 50 },
        email: { type: "email" },
      },
      handler: (ctx) => Handler.createProfile(ctx),
    },
  },
};
```

### `UserProfileServiceNode/src/UserProfileServiceNode/UserProfileServiceNode.gateway.js`

The HTTP server (moleculer-web). Logs the incoming correlation ID when one is present.

```javascript
const ApiService = require("moleculer-web");
const config = require("./config.json");
const logger = require("./winston.logger.service");

module.exports = {
  name: "api",
  mixins: [ApiService],
  settings: {
    port: config.app.port,
    ip: "0.0.0.0",
    routes: [
      {
        path: "/",
        autoAliases: true,
        whitelist: ["v1.userProfileService.*"],
        mergeParams: true,
        onBeforeCall(ctx, route, req) {
          const correlationId = req.headers["x-correlation-id"];
          if (correlationId) {
            logger.info(`[${correlationId}] Incoming request: ${req.method} ${req.url}`);
          }
        },
      },
    ],
  },
};
```

### `UserProfileServiceNode/src/UserProfileServiceNode/eureka.client.js`

Registers with Eureka and de-registers on shutdown.

```javascript
const { Eureka } = require("eureka-js-client");
const config = require("./config.json");
const logger = require("./winston.logger.service");

let client = null;

function startEureka() {
  return new Promise((resolve, reject) => {
    const { name, host, port } = config.app;

    client = new Eureka({
      instance: {
        app: name,
        instanceId: `${host}:${name}:${port}`,
        hostName: host,
        ipAddr: "127.0.0.1",
        vipAddress: name.toLowerCase(),
        port: { $: port, "@enabled": true },
        homePageUrl: `http://${host}:${port}/`,
        statusPageUrl: `http://${host}:${port}/v1/userprofile/health`,
        healthCheckUrl: `http://${host}:${port}/v1/userprofile/health`,
        dataCenterInfo: {
          "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
          name: "MyOwn",
        },
      },
      eureka: {
        host: config.eureka.host,
        port: config.eureka.port,
        servicePath: config.eureka.servicePath,
      },
    });

    client.start((err) => {
      if (err) return reject(err);
      logger.info(`Registered with Eureka as ${name}`);
      resolve(client);
    });
  });
}

function stopEureka() {
  return new Promise((resolve) => {
    if (!client) return resolve();
    client.stop(() => {
      logger.info("De-registered from Eureka");
      resolve();
    });
  });
}

module.exports = { startEureka, stopEureka };
```

### `UserProfileServiceNode/src/UserProfileServiceNode/index.js`

Entry point. Creates the broker, starts it, registers with Eureka, handles Ctrl+C.

```javascript
const { ServiceBroker } = require("moleculer");
const config = require("./config.json");
const logger = require("./winston.logger.service");
const gatewayService = require("./UserProfileServiceNode.gateway");
const userProfileService = require("./UserProfileServiceNode.service");
const { startEureka, stopEureka } = require("./eureka.client");
const { BadRequestError } = require("../errors");

async function main() {
  const broker = new ServiceBroker({
    namespace: config.moleculer.namespace,
    nodeID: "user-profile-node",
    skipProcessEventRegistration: true,
    logLevel: config.moleculer.logLevel,
    errorHandler(err, info) {
      if (err.name === "ValidationError") {
        const details = err.data.map((e) => ({ field: e.field, message: e.message }));
        throw new BadRequestError("Invalid request. Please check the fields listed in data.", details);
      }
      throw err;
    },
  });

  broker.createService(userProfileService);
  broker.createService(gatewayService);

  await broker.start();
  logger.info(`HTTP up on http://localhost:${config.app.port}/v1/userprofile/health`);

  await startEureka();

  process.on("SIGINT", async () => {
    await stopEureka();
    await broker.stop();
    process.exit(0);
  });
}

main();
```


---

## 9B. ConsumerServiceNode


### `ConsumerServiceNode/package.json`

Project manifest.

```json
{
  "name": "consumerservicenode",
  "version": "1.0.0",
  "description": "Moleculer POC - Consumer microservice (calls User Profile service via Eureka + axios)",
  "main": "src/ConsumerServiceNode/index.js",
  "scripts": {
    "start": "node src/ConsumerServiceNode/index.js"
  },
  "license": "ISC",
  "type": "commonjs",
  "dependencies": {
    "axios": "^1.20.0",
    "eureka-js-client": "^4.5.0",
    "moleculer": "^0.14.36",
    "moleculer-web": "^0.10.8",
    "winston": "^3.19.0"
  }
}
```

### `ConsumerServiceNode/.gitignore`

```
node_modules
*.sqlite
logs
```

### `ConsumerServiceNode/src/errors/index.js`

Same custom 400 exception as the other service.

```javascript
const { Errors } = require("moleculer");

class BadRequestError extends Errors.MoleculerError {
  constructor(message, details) {
    super(message, 400, "BAD_REQUEST", details);
    this.name = "BadRequestError";
  }
}

module.exports = { BadRequestError };
```

### `ConsumerServiceNode/src/ConsumerServiceNode/config.json`

Settings, including which Java service to call (`javaService`).

```json
{
  "app": {
    "name": "CONSUMER-SERVICE",
    "host": "localhost",
    "port": 3002
  },
  "moleculer": {
    "namespace": "poc",
    "logLevel": "info"
  },
  "eureka": {
    "host": "localhost",
    "port": 8761,
    "servicePath": "/eureka/apps/"
  },
  "javaService": {
    "appName": "CUSTOMER-SERVICE",
    "path": "/api/customers"
  }
}
```

### `ConsumerServiceNode/src/ConsumerServiceNode/winston.logger.service.js`

Identical to the other service's logger.

```javascript
const winston = require("winston");

module.exports = winston.createLogger({
  level: "info",
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.printf(({ timestamp, level, message }) => `${timestamp} [${level}] ${message}`)
  ),
  transports: [new winston.transports.Console()],
});
```

### `ConsumerServiceNode/src/ConsumerServiceNode/eureka.client.js`

Registers itself and looks up other services from a cached copy of the registry (`fetchRegistry: true`).

```javascript
const { Eureka } = require("eureka-js-client");
const config = require("./config.json");
const logger = require("./winston.logger.service");

let client = null;

function startEureka() {
  return new Promise((resolve, reject) => {
    const { name, host, port } = config.app;

    client = new Eureka({
      instance: {
        app: name,
        instanceId: `${host}:${name}:${port}`,
        hostName: host,
        ipAddr: "127.0.0.1",
        vipAddress: name.toLowerCase(),
        port: { $: port, "@enabled": true },
        homePageUrl: `http://${host}:${port}/`,
        statusPageUrl: `http://${host}:${port}/v1/consumer/health`,
        healthCheckUrl: `http://${host}:${port}/v1/consumer/health`,
        dataCenterInfo: {
          "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
          name: "MyOwn",
        },
      },
      eureka: {
        host: config.eureka.host,
        port: config.eureka.port,
        servicePath: config.eureka.servicePath,
        fetchRegistry: true,
      },
    });

    client.start((err) => {
      if (err) return reject(err);
      logger.info(`Registered with Eureka as ${name}`);
      resolve(client);
    });
  });
}

function stopEureka() {
  return new Promise((resolve) => {
    if (!client) return resolve();
    client.stop(() => {
      logger.info("De-registered from Eureka");
      resolve();
    });
  });
}

function findServiceUrl(appName) {
  const instances = client.getInstancesByAppId(appName);
  if (!instances || instances.length === 0) {
    throw new Error(`${appName} is not available in Eureka`);
  }
  const instance = instances[0];
  return `http://${instance.hostName}:${instance.port["$"]}`;
}

function findUserProfileServiceUrl() {
  return findServiceUrl("USER-PROFILE-SERVICE");
}

module.exports = { startEureka, stopEureka, findUserProfileServiceUrl, findServiceUrl };
```

### `ConsumerServiceNode/src/ConsumerServiceNode/ConsumerServiceNode.handler.js`

Calls the Node service and the Java service with axios, forwarding the correlation ID.

```javascript
const axios = require("axios");
const { randomUUID } = require("crypto");
const config = require("./config.json");
const { findUserProfileServiceUrl, findServiceUrl } = require("./eureka.client");
const logger = require("./winston.logger.service");

const health = async () => {
  return { status: "UP" };
};

const getUserProfile = async (ctx) => {
  const baseUrl = findUserProfileServiceUrl();
  const url = `${baseUrl}/v1/userprofile/profiles/${ctx.params.userId}`;

  const correlationId = ctx.meta.correlationId || randomUUID();

  logger.info(`[${correlationId}] Calling ${url}`);

  const response = await axios.get(url, {
    headers: { "X-Correlation-Id": correlationId },
  });

  return response.data;
};

const getCustomerFromJava = async (ctx) => {
  const baseUrl = findServiceUrl(config.javaService.appName);
  const url = `${baseUrl}${config.javaService.path}/${ctx.params.id}`;

  const correlationId = ctx.meta.correlationId || randomUUID();

  logger.info(`[${correlationId}] Calling Java service ${url}`);

  const response = await axios.get(url, {
    headers: { "X-Correlation-Id": correlationId },
  });

  return response.data;
};

module.exports = { health, getUserProfile, getCustomerFromJava };
```

### `ConsumerServiceNode/src/ConsumerServiceNode/ConsumerServiceNode.service.js`

URLs and validation.

```javascript
const Handler = require("./ConsumerServiceNode.handler");

module.exports = {
  name: "consumerService",
  version: 1,
  settings: {
    rest: "/v1/consumer",
  },

  actions: {
    health: {
      rest: "GET /health",
      handler: (ctx) => Handler.health(ctx),
    },

    getUserProfile: {
      rest: "GET /user-profile/:userId",
      params: {
        userId: { type: "string", min: 1, max: 20 },
      },
      handler: (ctx) => Handler.getUserProfile(ctx),
    },

    getCustomerFromJava: {
      rest: "GET /java-customer/:id",
      params: {
        id: { type: "string", min: 1, max: 20 },
      },
      handler: (ctx) => Handler.getCustomerFromJava(ctx),
    },
  },
};
```

### `ConsumerServiceNode/src/ConsumerServiceNode/ConsumerServiceNode.gateway.js`

HTTP server. Copies the caller's `X-Correlation-Id` header into `ctx.meta`.

```javascript
const ApiService = require("moleculer-web");
const config = require("./config.json");

module.exports = {
  name: "api",
  mixins: [ApiService],
  settings: {
    port: config.app.port,
    ip: "0.0.0.0",
    routes: [
      {
        path: "/",
        autoAliases: true,
        whitelist: ["v1.consumerService.*"],
        mergeParams: true,
        onBeforeCall(ctx, route, req) {
          const incomingId = req.headers["x-correlation-id"];
          ctx.meta.correlationId = incomingId;
        },
      },
    ],
  },
};
```

### `ConsumerServiceNode/src/ConsumerServiceNode/index.js`

Entry point. Same shape as the other service, with a different `nodeID`.

```javascript
const { ServiceBroker } = require("moleculer");
const config = require("./config.json");
const logger = require("./winston.logger.service");
const gatewayService = require("./ConsumerServiceNode.gateway");
const consumerService = require("./ConsumerServiceNode.service");
const { startEureka, stopEureka } = require("./eureka.client");
const { BadRequestError } = require("../errors");

async function main() {
  const broker = new ServiceBroker({
    namespace: config.moleculer.namespace,
    nodeID: "consumer-node",
    skipProcessEventRegistration: true,
    logLevel: config.moleculer.logLevel,
    errorHandler(err, info) {
      if (err.name === "ValidationError") {
        const details = err.data.map((e) => ({ field: e.field, message: e.message }));
        throw new BadRequestError("Invalid request. Please check the fields listed in data.", details);
      }
      throw err;
    },
  });

  broker.createService(consumerService);
  broker.createService(gatewayService);

  await broker.start();
  logger.info(`HTTP up on http://localhost:${config.app.port}/v1/consumer/health`);

  await startEureka();

  process.on("SIGINT", async () => {
    await stopEureka();
    await broker.stop();
    process.exit(0);
  });
}

main();
```


---

## 10. Optional test tools

Use these only when the real Eureka server or the real Java service is not available. They are not part of the deliverable application.

### `tools/mock-eureka/package.json`

```json
{ "name": "mock-eureka", "version": "1.0.0", "private": true, "scripts": { "start": "node server.js" } }
```

### `tools/mock-eureka/server.js`

A small stand-in for Eureka. It implements only register, heartbeat, de-register and list. Run it with `node server.js` from `tools/mock-eureka`. It listens on port 8761. **Do not run it while the real Eureka is running.**

```javascript
/**
 * Minimal Eureka server (for local POC only).
 * If your office already has a real Eureka server, DON'T use this - just point config.json to it.
 */
const http = require("http");
const PORT = process.env.PORT || 8761;
const apps = {}; // { APPNAME: { instanceId: instanceObj } }

const send = (res, code, body) => {
  res.writeHead(code, { "Content-Type": "application/json" });
  res.end(body ? JSON.stringify(body) : "");
};
const readBody = (req) =>
  new Promise((resolve) => {
    let d = "";
    req.on("data", (c) => (d += c));
    req.on("end", () => resolve(d ? JSON.parse(d) : {}));
  });
const appList = () =>
  Object.keys(apps).map((name) => ({ name, instance: Object.values(apps[name]) }));

http
  .createServer(async (req, res) => {
    const url = new URL(req.url, "http://localhost");
    const parts = url.pathname.split("/").filter(Boolean); // ["eureka","apps",APP,ID]
    const [, , appId, instanceId] = parts;
    const APP = appId && appId.toUpperCase();

    if (parts.length === 0) {
      res.writeHead(200, { "Content-Type": "text/html" });
      return res.end(
        "<h2>Mock Eureka</h2><pre>" +
          JSON.stringify(appList().map((a) => ({ app: a.name, instances: a.instance.map((i) => i.instanceId + " " + i.status) })), null, 2) +
          "</pre>"
      );
    }
    if (parts[0] !== "eureka" || parts[1] !== "apps") return send(res, 404);

    if (req.method === "POST" && APP && !instanceId) {
      const { instance } = await readBody(req);
      apps[APP] = apps[APP] || {};
      apps[APP][instance.instanceId] = { ...instance, status: "UP" };
      console.log("REGISTER  ", APP, instance.instanceId);
      return send(res, 204);
    }
    if (req.method === "PUT" && APP && instanceId) {
      if (!apps[APP] || !apps[APP][instanceId]) return send(res, 404);
      return send(res, 200); // heartbeat
    }
    if (req.method === "DELETE" && APP && instanceId) {
      if (apps[APP]) {
        delete apps[APP][instanceId];
        if (Object.keys(apps[APP]).length === 0) delete apps[APP];
      }
      console.log("DEREGISTER", APP, instanceId);
      return send(res, 200);
    }
    if (req.method === "GET" && !APP) {
      return send(res, 200, {
        applications: { versions__delta: "1", apps__hashcode: "UP_" + appList().length + "_", application: appList() },
      });
    }
    if (req.method === "GET" && APP && !instanceId) {
      if (!apps[APP]) return send(res, 404);
      return send(res, 200, { application: { name: APP, instance: Object.values(apps[APP]) } });
    }
    send(res, 404);
  })
  .listen(PORT, () => console.log("Mock Eureka running on http://localhost:" + PORT + "/eureka/apps"));
```

### `tools/fake-customer-service.js`

A stand-in for the existing Java `customer-service`. It listens on 8081, registers itself in Eureka the way Spring Cloud does, and answers `GET /api/customers/1`. Anything else returns 500. Run it with `node fake-customer-service.js`, after Eureka is up and **before** the Consumer starts. **Do not run it while the real Java service is running.**

```javascript
const http = require("http");
http.createServer((req, res) => {
  console.log("FAKE-CUSTOMER-SERVICE got", req.method, req.url, "| x-correlation-id =", req.headers["x-correlation-id"]);
  if (req.url === "/api/customers/1") { res.writeHead(200, {"Content-Type":"application/json"}); return res.end(JSON.stringify({ id: 1, name: "Raj Kumar", email: "raj@test.com", phone: "9999999999", active: false, createdDate: "2026-09-14T17:20:33.493247" })); }
  res.writeHead(500, {"Content-Type":"application/json"}); res.end(JSON.stringify({ status: 500, error: "Internal Server Error" }));
}).listen(8081, () => {
  const body = JSON.stringify({ instance: { instanceId: "localhost:customer-service:8081", hostName: "localhost", app: "CUSTOMER-SERVICE", ipAddr: "192.168.1.10",
    vipAddress: "customer-service", secureVipAddress: "customer-service", status: "UP", port: { "$": 8081, "@enabled": "true" }, securePort: { "$": 443, "@enabled": "false" },
    homePageUrl: "http://localhost:8081/", statusPageUrl: "http://localhost:8081/actuator/info", healthCheckUrl: "http://localhost:8081/actuator/health",
    dataCenterInfo: { "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo", name: "MyOwn" } } });
  const r = http.request({ host: "localhost", port: 8761, path: "/eureka/apps/CUSTOMER-SERVICE", method: "POST", headers: { "Content-Type": "application/json" } }, (x) => console.log("FAKE-CUSTOMER-SERVICE registered, HTTP", x.statusCode));
  r.write(body); r.end();
});
```

---

## 11. Run order

Start these in separate terminals, in this order, waiting for each to finish starting:

1. PostgreSQL running, `userprofile_db` created.
2. Eureka (real, or `node tools/mock-eureka/server.js`).
3. Java `customer-service` (real, or the fake).
4. `cd UserProfileServiceNode && node src/UserProfileServiceNode/index.js`. Wait for `Registered with Eureka as USER-PROFILE-SERVICE`.
5. `cd ConsumerServiceNode && node src/ConsumerServiceNode/index.js`. Wait for `Registered with Eureka as CONSUMER-SERVICE`.

Stopping with Ctrl+C should print `De-registered from Eureka` in each terminal.

---

## 12. Acceptance checks

Run every check. These all passed on the code in this document. Bodies are shortened.

**UserProfileServiceNode**

| # | Request | Expected |
|---|---|---|
| 1 | `GET :3001/v1/userprofile/hello` | 200, `{"message":"Hello from UserProfileServiceNode","service":"USER-PROFILE-SERVICE","time":"..."}` |
| 2 | `GET :3001/v1/userprofile/health` | 200, `{"status":"UP"}` |
| 3 | `GET :3001/v1/userprofile/profiles` on an empty table | 200, `[]` |
| 4 | `POST :3001/v1/userprofile/profiles` with `{"userId":"9001","firstName":"","email":"nope"}` | **400**, `name: BadRequestError`, `data` lists `firstName`, `lastName` and `email` |
| 5 | `POST` with `{"userId":"1001","firstName":"Prem","lastName":"Sai","email":"prem@example.com"}` | 200, the row with `id: 1`, `createdAt`, `updatedAt` |
| 6 | `GET :3001/v1/userprofile/profiles/1001` | 200, the same row |
| 7 | `POST` the same `userId` again | 500, `SequelizeUniqueConstraintError` (known, section 13) |
| 8 | `GET :3001/v1/userprofile/profiles/9999` | 500, `No profile found for userId 9999` (known, section 13) |

**ConsumerServiceNode, calling Node**

| # | Request | Expected |
|---|---|---|
| 9 | `GET :3002/v1/consumer/health` | 200, `{"status":"UP"}` |
| 10 | `GET :3002/v1/consumer/user-profile/1001` | 200, the profile returned by the other service |
| 11 | Same, with header `X-Correlation-Id: demo-123` | 200. Consumer log shows `[demo-123] Calling http://localhost:3001/...` and the UserProfile log shows `[demo-123] Incoming request: GET /v1/userprofile/profiles/1001` |
| 12 | Same, with no header | 200. Both logs show the **same** generated UUID |
| 13 | `GET :3002/v1/consumer/user-profile/` followed by 38 digits | **400**, `BadRequestError`, `data` names `userId` |

**ConsumerServiceNode, calling Java**

| # | Request | Expected |
|---|---|---|
| 14 | `GET :3002/v1/consumer/java-customer/1` | 200, the customer JSON from the Java service. Consumer log shows `Calling Java service http://localhost:8081/api/customers/1` |
| 15 | Same, with header `X-Correlation-Id: java-456` | 200. The Java service receives the same header value |
| 16 | `GET :3002/v1/consumer/java-customer/` followed by 38 digits | **400**, `BadRequestError`, `data` names `id` |

**Eureka**

| # | Check | Expected |
|---|---|---|
| 17 | Eureka dashboard or `GET :8761/eureka/apps` | `USER-PROFILE-SERVICE`, `CONSUMER-SERVICE` and `CUSTOMER-SERVICE` all listed as UP |
| 18 | Ctrl+C the Consumer, then the UserProfile service | Each prints `De-registered from Eureka`, and both disappear from the registry |

Also confirm the row from check 5 exists in PostgreSQL:

```sql
SELECT id, "userId", "firstName", "lastName", email FROM user_profile;
```

---

## 13. Known weaknesses: keep them as they are

These are deliberate gaps in the reference. Reproduce them. Do not fix them unless the user asks in a separate request.

- **Not found and duplicates return 500.** A missing `userId` and a duplicate `userId` both return a generic 500. They should be 404 and 409, using custom errors like `BadRequestError`.
- **Consumer hides downstream errors.** If the Node or Java service returns an error, or is down, the Consumer returns a generic 500 with an `AxiosError` body. It does not pass the real status or message on.
- **No authentication between services.** Any process that can reach port 3001 can call the User Profile service directly. The correlation ID traces a request across logs. It does not prove who sent it.
- **The database password is in `config.json`.** In a real service it would come from a secrets store such as Azure Key Vault.
- **`sync()` instead of migrations.** It creates missing tables but never alters existing ones.
- **The Consumer uses the first instance** returned for a service name. There is no choice between several running copies.
- **Eureka needs to be up at start-up.** A running service survives Eureka going down because of the cached registry, but a service that starts while Eureka is down cannot register and will not start.
- **The admin `postgres` user is used** for the connection. A real service would have its own restricted user.

---

## 14. Out of scope

- **Java calling Node** (a Feign client in the Java `composite-service` that calls `USER-PROFILE-SERVICE`). Not part of this build.
- **The Java services themselves.** They already exist.
- **An API gateway, JWT or any authentication.**
- **Docker, CI, tests and deployment.**
- **A Postman collection.** One exists separately. If asked to create one, use the requests in section 12.

---

## 15. Done means

The application is finished only when **all 18 checks in section 12 pass** and both services de-register cleanly on Ctrl+C. Report the result of each check to the user, and list anything that differs from this document.
