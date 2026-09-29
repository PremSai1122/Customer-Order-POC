/**
 * Minimal Eureka server (for local POC only).
 * If your office already has a real Eureka server, DON'T use this - just point config.json to it.
 */
const http = require("http");
const PORT = process.env.PORT || 8761;
const apps = {}; // { APPNAME: { instanceId: instanceObj } }

const send = (res, code, body) => {
  res.writeHead(code, { "Content-Type": "application/json" });
  res.end(body ? JSON.stringify(body) : "");
};
const readBody = (req) =>
  new Promise((resolve) => {
    let d = "";
    req.on("data", (c) => (d += c));
    req.on("end", () => resolve(d ? JSON.parse(d) : {}));
  });
const appList = () =>
  Object.keys(apps).map((name) => ({ name, instance: Object.values(apps[name]) }));

http
  .createServer(async (req, res) => {
    const url = new URL(req.url, "http://localhost");
    const parts = url.pathname.split("/").filter(Boolean); // ["eureka","apps",APP,ID]
    const [, , appId, instanceId] = parts;
    const APP = appId && appId.toUpperCase();

    if (parts.length === 0) {
      res.writeHead(200, { "Content-Type": "text/html" });
      return res.end(
        "<h2>Mock Eureka</h2><pre>" +
          JSON.stringify(appList().map((a) => ({ app: a.name, instances: a.instance.map((i) => i.instanceId + " " + i.status) })), null, 2) +
          "</pre>"
      );
    }
    if (parts[0] !== "eureka" || parts[1] !== "apps") return send(res, 404);

    if (req.method === "POST" && APP && !instanceId) {
      const { instance } = await readBody(req);
      apps[APP] = apps[APP] || {};
      apps[APP][instance.instanceId] = { ...instance, status: "UP" };
      console.log("REGISTER  ", APP, instance.instanceId);
      return send(res, 204);
    }
    if (req.method === "PUT" && APP && instanceId) {
      if (!apps[APP] || !apps[APP][instanceId]) return send(res, 404);
      return send(res, 200); // heartbeat
    }
    if (req.method === "DELETE" && APP && instanceId) {
      if (apps[APP]) {
        delete apps[APP][instanceId];
        if (Object.keys(apps[APP]).length === 0) delete apps[APP];
      }
      console.log("DEREGISTER", APP, instanceId);
      return send(res, 200);
    }
    if (req.method === "GET" && !APP) {
      return send(res, 200, {
        applications: { versions__delta: "1", apps__hashcode: "UP_" + appList().length + "_", application: appList() },
      });
    }
    if (req.method === "GET" && APP && !instanceId) {
      if (!apps[APP]) return send(res, 404);
      return send(res, 200, { application: { name: APP, instance: Object.values(apps[APP]) } });
    }
    send(res, 404);
  })
  .listen(PORT, () => console.log("Mock Eureka running on http://localhost:" + PORT + "/eureka/apps"));
