import express from "express";
import helmet from "helmet";
import cors from "cors";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";
import rateLimit from "express-rate-limit";
import crypto from "node:crypto";

const { Pool } = pg;
const app = express();
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(",") : false }));
app.use(express.json({ limit: "32kb" }));
app.use("/api/", rateLimit({ windowMs: 60_000, limit: 120, standardHeaders: "draft-7", legacyHeaders: false }));

const required = ["DATABASE_URL", "JWT_SECRET", "OWNER_SETUP_KEY"];
for (const key of required) if (!process.env[key]) throw new Error(`Missing environment variable: ${key}`);
if (process.env.JWT_SECRET.length < 32 || process.env.OWNER_SETUP_KEY.length < 20) throw new Error("JWT_SECRET must be 32+ chars and OWNER_SETUP_KEY 20+ chars");
const pool = new Pool({ connectionString: process.env.DATABASE_URL, ssl: process.env.PGSSLMODE === "disable" ? false : { rejectUnauthorized: false } });

await pool.query(`
CREATE TABLE IF NOT EXISTS users (
 id BIGSERIAL PRIMARY KEY, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL,
 role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user','owner')), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS devices (
 id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 name TEXT NOT NULL, device_secret_hash TEXT NOT NULL, last_seen TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS pairings (
 id UUID PRIMARY KEY, device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
 code_hash TEXT NOT NULL, status TEXT NOT NULL CHECK (status IN ('pending','claimed','active','revoked','expired')),
 expires_at TIMESTAMPTZ NOT NULL, claimed_by BIGINT REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS commands (
 id BIGSERIAL PRIMARY KEY, pairing_id UUID NOT NULL REFERENCES pairings(id) ON DELETE CASCADE,
 command JSONB NOT NULL, status TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','accepted','completed','rejected')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS commands_queue_idx ON commands(pairing_id,status,id);
`);

const auth = (req,res,next) => {
 try { const token=(req.headers.authorization||"").replace(/^Bearer\s+/i,""); if(!token) return res.status(401).json({error:"Authentication required"});
 const data=jwt.verify(token,process.env.JWT_SECRET); req.user=data; next(); }
 catch { return res.status(401).json({error:"Invalid or expired token"}); }
};
const owner = (req,res,next) => req.user.role === "owner" && req.user.username === "12111" ? next() : res.status(403).json({error:"Owner account 12111 required"});
const tokenFor = u => jwt.sign({sub:String(u.id),username:u.username,role:u.role},process.env.JWT_SECRET,{expiresIn:"7d"});
const safeUser = u => ({username:u.username,role:u.role});
const randomCode = () => String(crypto.randomInt(0,1000000)).padStart(6,"0");
const hash = s => crypto.createHash("sha256").update(s).digest("hex");

app.get("/",(_req,res)=>res.type("html").send(`<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CORTEX CONTROL API</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#080d18;color:#eaf3ff;font:16px system-ui,sans-serif}.card{max-width:680px;margin:24px;padding:32px;border:1px solid #1d4261;border-radius:22px;background:linear-gradient(145deg,#101d31,#09111f);box-shadow:0 20px 80px #0008}h1{letter-spacing:.12em;color:#55d9ff}p{line-height:1.6;color:#b8c8dc}.status{color:#7dffbd}.links a{color:#55d9ff;margin-right:18px}</style></head>
<body><main class="card"><h1>CORTEX CONTROL</h1><p class="status">● API service is online</p><p>Сервер приложения CORTEX CONTROL. Он обслуживает авторизацию, безопасное сопряжение устройств и передачу разрешённых команд.</p><p class="links"><a href="/health">Проверить API</a><a href="/api">API</a></p></main></body></html>`));
app.get("/health",(_req,res)=>res.json({ok:true,service:"CORTEX CONTROL",version:"1.0.0"}));

app.post("/api/auth/register",async(req,res)=>{
 try {
  const username=String(req.body.username||"").trim().toLowerCase(), password=String(req.body.password||"");
  if(!/^[a-z0-9_]{3,24}$/.test(username)||username==="12111") return res.status(400).json({error:"Username must be 3-24 letters, digits or underscores. 12111 is reserved."});
  if(password.length<10||password.length>128) return res.status(400).json({error:"Password must contain 10-128 characters"});
  const ph=await bcrypt.hash(password,12);
  const q=await pool.query("INSERT INTO users(username,password_hash,role) VALUES($1,$2,'user') RETURNING id,username,role",[username,ph]);
  const u=q.rows[0]; return res.status(201).json({token:tokenFor(u),user:safeUser(u)});
 } catch(e) { if(e.code==="23505") return res.status(409).json({error:"Username already exists"}); console.error(e); return res.status(500).json({error:"Server error"}); }
});

app.post("/api/auth/register-owner",async(req,res)=>{
 try {
  const key=String(req.headers["x-owner-setup-key"]||"");
  const suppliedKey=Buffer.from(key); const expectedKey=Buffer.from(process.env.OWNER_SETUP_KEY);\n  if(!key || suppliedKey.length!==expectedKey.length || !crypto.timingSafeEqual(suppliedKey,expectedKey)) return res.status(403).json({error:"Invalid owner setup key"});
  const password=String(req.body.password||"");
  if(password.length<12||password.length>128) return res.status(400).json({error:"Owner password must contain at least 12 characters"});
  const existing=await pool.query("SELECT id FROM users WHERE role='owner' OR username='12111'");
  if(existing.rowCount) return res.status(409).json({error:"Owner account already initialized"});
  const ph=await bcrypt.hash(password,12);
  const q=await pool.query("INSERT INTO users(username,password_hash,role) VALUES('12111',$1,'owner') RETURNING id,username,role",[ph]);
  const u=q.rows[0]; return res.status(201).json({token:tokenFor(u),user:safeUser(u)});
 } catch(e) { console.error(e); return res.status(500).json({error:"Server error"}); }
});

app.post("/api/auth/login",async(req,res)=>{
 try {
  const username=String(req.body.username||"").trim().toLowerCase(), password=String(req.body.password||"");
  const q=await pool.query("SELECT id,username,password_hash,role FROM users WHERE username=$1",[username]);
  const u=q.rows[0]; if(!u||!(await bcrypt.compare(password,u.password_hash))) return res.status(401).json({error:"Incorrect username or password"});
  return res.json({token:tokenFor(u),user:safeUser(u)});
 } catch(e){console.error(e);return res.status(500).json({error:"Server error"});}
});

app.get("/api/me",auth,(req,res)=>res.json({username:req.user.username,role:req.user.role,isOwner:req.user.role==="owner"&&req.user.username==="12111"}));

app.post("/api/devices/register",auth,async(req,res)=>{
 try {
  const name=String(req.body.name||"Android device").trim().slice(0,60);
  const id=crypto.randomUUID(), secret=crypto.randomBytes(32).toString("hex");
  await pool.query("INSERT INTO devices(id,user_id,name,device_secret_hash,last_seen) VALUES($1,$2,$3,$4,now())",[id,Number(req.user.sub),name,hash(secret)]);
  res.status(201).json({deviceId:id,deviceSecret:secret,note:"Store deviceSecret securely on this device. Never share it."});
 }catch(e){console.error(e);res.status(500).json({error:"Server error"});}
});

// Device endpoints require the device's private secret, proving that requests come from the paired phone.
const deviceAuth=async(req,res,next)=>{
 try { const id=req.header("x-device-id"), secret=req.header("x-device-secret"); if(!id||!secret)return res.status(401).json({error:"Device credentials required"});
 const q=await pool.query("SELECT id,user_id,device_secret_hash FROM devices WHERE id=$1",[id]); const d=q.rows[0];
 if(!d||hash(secret)!==d.device_secret_hash)return res.status(401).json({error:"Invalid device credentials"});
 req.device=d; await pool.query("UPDATE devices SET last_seen=now() WHERE id=$1",[id]); next();
 }catch(e){console.error(e);res.status(500).json({error:"Server error"});}
};

app.post("/api/pairing/request",auth,async(req,res)=>{
 try {
  const name=String(req.body.deviceName||"My phone").trim().slice(0,60), id=crypto.randomUUID(), secret=crypto.randomBytes(32).toString("hex"), code=randomCode();
  await pool.query("INSERT INTO devices(id,user_id,name,device_secret_hash,last_seen) VALUES($1,$2,$3,$4,now())",[id,Number(req.user.sub),name,hash(secret)]);
  await pool.query("INSERT INTO pairings(id,device_id,code_hash,status,expires_at) VALUES($1,$2,$3,'pending',now()+interval '10 minutes')",[crypto.randomUUID(),id,hash(code)]);
  res.status(201).json({deviceId:id,deviceSecret:secret,pairingCode:code,expiresInSeconds:600,instructions:"Share this code with the owner account 12111. The owner can request pairing; confirm it on this phone."});
 }catch(e){console.error(e);res.status(500).json({error:"Server error"});}
});

app.post("/api/pairing/claim",auth,owner,async(req,res)=>{
 try {
  const code=String(req.body.code||"").trim(); if(!/^\d{6}$/.test(code))return res.status(400).json({error:"Enter a six-digit code"});
  const q=await pool.query("UPDATE pairings SET status='claimed',claimed_by=$2 WHERE code_hash=$1 AND status='pending' AND expires_at>now() RETURNING id,device_id",[hash(code),Number(req.user.sub)]);
  if(!q.rowCount)return res.status(404).json({error:"Code not found, expired or already used"});
  const d=await pool.query("SELECT name FROM devices WHERE id=$1",[q.rows[0].device_id]);
  res.json({pairingId:q.rows[0].id,deviceName:d.rows[0]?.name||"Device",status:"awaiting_device_approval"});
 }catch(e){console.error(e);res.status(500).json({error:"Server error"});}
});

app.get("/api/pairing/pending",deviceAuth,async(req,res)=>{
 const q=await pool.query("SELECT id,status,expires_at FROM pairings WHERE device_id=$1 AND status='claimed' AND expires_at>now() ORDER BY created_at DESC LIMIT 1",[req.device.id]);
 res.json({pending:q.rowCount>0,pairing:q.rows[0]||null});
});

app.post("/api/pairing/:id/approve",deviceAuth,async(req,res)=>{
 try {
  const q=await pool.query("UPDATE pairings SET status='active', expires_at=now()+interval '10 years' WHERE id=$1 AND device_id=$2 AND status='claimed' AND expires_at>now() RETURNING id",[req.params.id,req.device.id]);
  if(!q.rowCount)return res.status(404).json({error:"No matching pending pairing"});
  res.json({ok:true,status:"active",message:"Remote access is now enabled. You can revoke it at any time."});
 }catch(e){console.error(e);res.status(500).json({error:"Server error"});}
});

app.post("/api/pairing/:id/revoke",deviceAuth,async(req,res)=>{
 const q=await pool.query("UPDATE pairings SET status='revoked' WHERE id=$1 AND device_id=$2 AND status='active' RETURNING id",[req.params.id,req.device.id]);
 if(!q.rowCount)return res.status(404).json({error:"Active pairing not found"});
 res.json({ok:true,status:"revoked"});
});

app.get("/api/devices",auth,owner,async(req,res)=>{
 const q=await pool.query("SELECT d.id,d.name,d.last_seen,p.id AS pairing_id,p.status AS pairing_status FROM devices d JOIN pairings p ON p.device_id=d.id WHERE p.claimed_by=$1 AND p.status='active' ORDER BY d.last_seen DESC",[Number(req.user.sub)]);
 res.json({devices:q.rows});
});

app.post("/api/devices/:id/commands",auth,owner,async(req,res)=>{
 try {
  const allowed=new Set(["OPEN_APP","GO_HOME","DIAL_CONTACT","COMPOSE_SMS","LOCK_SCREEN"]);
  const type=String(req.body.type||""); if(!allowed.has(type))return res.status(400).json({error:"Unsupported command"});
  const payload=req.body.payload??{}; if(JSON.stringify(payload).length>2000)return res.status(400).json({error:"Payload too large"});
  const p=await pool.query("SELECT p.id FROM pairings p JOIN devices d ON d.id=p.device_id WHERE d.id=$1 AND p.claimed_by=$2 AND p.status='active' AND p.expires_at>now() LIMIT 1",[req.params.id,Number(req.user.sub)]);
  if(!p.rowCount)return res.status(403).json({error:"Device is not actively paired or consent was revoked"});
  const q=await pool.query("INSERT INTO commands(pairing_id,command,expires_at) VALUES($1,$2,now()+interval '60 seconds') RETURNING id",[p.rows[0].id,JSON.stringify({type,payload})]);
  res.status(202).json({commandId:q.rows[0].id,status:"queued"});
 }catch(e){console.error(e);res.status(500).json({error:"Server error"});}
});

app.get("/api/device/commands",deviceAuth,async(req,res)=>{
 try {
  const q=await pool.query("SELECT c.id,c.command FROM commands c JOIN pairings p ON p.id=c.pairing_id WHERE p.device_id=$1 AND p.status='active' AND c.status='queued' AND c.expires_at>now() ORDER BY c.id LIMIT 10",[req.device.id]);
  if(q.rowCount)await pool.query("UPDATE commands SET status='accepted' WHERE id=ANY($1::bigint[])",[q.rows.map(x=>x.id)]);
  res.json({commands:q.rows.map(x=>({id:x.id,...x.command}))});
 }catch(e){console.error(e);res.status(500).json({error:"Server error"});}
});

app.post("/api/device/commands/:id/result",deviceAuth,async(req,res)=>{
 const status=String(req.body.status||""); if(!["completed","rejected"].includes(status))return res.status(400).json({error:"Invalid result status"});
 const q=await pool.query("UPDATE commands c SET status=$1 FROM pairings p WHERE c.pairing_id=p.id AND p.device_id=$2 AND p.status='active' AND c.id=$3 AND c.status='accepted' RETURNING c.id",[status,req.device.id,req.params.id]);
 if(!q.rowCount)return res.status(404).json({error:"Command not found"});
 res.json({ok:true});
});

app.use((err,_req,res,_next)=>{console.error(err);res.status(500).json({error:"Internal server error"});});
const port=Number(process.env.PORT||3000);
app.listen(port,"0.0.0.0",()=>console.log(`CORTEX CONTROL API listening on ${port}`));
