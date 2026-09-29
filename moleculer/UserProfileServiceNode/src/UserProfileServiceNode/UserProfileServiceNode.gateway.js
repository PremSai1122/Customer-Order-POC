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
