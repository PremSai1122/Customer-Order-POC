const http = require("http");
http.createServer((req, res) => {
  console.log("FAKE-CUSTOMER-SERVICE got", req.method, req.url, "| x-correlation-id =", req.headers["x-correlation-id"]);
  if (req.url === "/api/customers/1") { res.writeHead(200, {"Content-Type":"application/json"}); return res.end(JSON.stringify({ id: 1, name: "Raj Kumar", email: "raj@test.com", phone: "9999999999", active: false, createdDate: "2026-09-14T17:20:33.493247" })); }
  res.writeHead(500, {"Content-Type":"application/json"}); res.end(JSON.stringify({ status: 500, error: "Internal Server Error" }));
}).listen(8081, () => {
  const body = JSON.stringify({ instance: { instanceId: "localhost:customer-service:8081", hostName: "localhost", app: "CUSTOMER-SERVICE", ipAddr: "192.168.1.10",
    vipAddress: "customer-service", secureVipAddress: "customer-service", status: "UP", port: { "$": 8081, "@enabled": "true" }, securePort: { "$": 443, "@enabled": "false" },
    homePageUrl: "http://localhost:8081/", statusPageUrl: "http://localhost:8081/actuator/info", healthCheckUrl: "http://localhost:8081/actuator/health",
    dataCenterInfo: { "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo", name: "MyOwn" } } });
  const r = http.request({ host: "localhost", port: 8761, path: "/eureka/apps/CUSTOMER-SERVICE", method: "POST", headers: { "Content-Type": "application/json" } }, (x) => console.log("FAKE-CUSTOMER-SERVICE registered, HTTP", x.statusCode));
  r.write(body); r.end();
});
