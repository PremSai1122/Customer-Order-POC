const { initUserProfileModel } = require("./model/UserProfile.model");

const hello = async () => {
  return {
    message: "Hello from UserProfileServiceNode",
    service: "USER-PROFILE-SERVICE",
    time: new Date().toISOString(),
  };
};

const health = async () => {
  return { status: "UP" };
};

const getAllProfiles = async () => {
  const UserProfile = await initUserProfileModel();
  return UserProfile.findAll();
};

const getProfile = async (ctx) => {
  const UserProfile = await initUserProfileModel();
  const profile = await UserProfile.findOne({ where: { userId: ctx.params.userId } });
  if (!profile) {
    throw new Error(`No profile found for userId ${ctx.params.userId}`);
  }
  return profile;
};

const createProfile = async (ctx) => {
  const UserProfile = await initUserProfileModel();
  return UserProfile.create(ctx.params);
};

module.exports = { hello, health, getAllProfiles, getProfile, createProfile };
