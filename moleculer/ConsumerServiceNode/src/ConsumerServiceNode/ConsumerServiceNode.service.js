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
