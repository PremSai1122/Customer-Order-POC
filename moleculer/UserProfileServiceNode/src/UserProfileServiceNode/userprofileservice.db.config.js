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
