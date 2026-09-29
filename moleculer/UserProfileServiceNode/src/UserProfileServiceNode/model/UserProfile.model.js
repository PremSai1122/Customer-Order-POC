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
