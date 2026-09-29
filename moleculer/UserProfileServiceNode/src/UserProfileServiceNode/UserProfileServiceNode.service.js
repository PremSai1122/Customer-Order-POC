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
