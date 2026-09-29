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
