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
