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
