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
