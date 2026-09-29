const { Errors } = require("moleculer");

class BadRequestError extends Errors.MoleculerError {
  constructor(message, details) {
    super(message, 400, "BAD_REQUEST", details);
    this.name = "BadRequestError";
  }
}

module.exports = { BadRequestError };
