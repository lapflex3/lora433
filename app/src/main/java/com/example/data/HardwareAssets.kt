package com.example.data

object HardwareAssets {

    val esp32Sketch = """
// ESP32 Transceiver Code: RF 433MHz ASK/OOK Receptor & SX1278 LoRa Node
// Hardware: ESP32 DevKitC, RX433 (or XY-FST), SX1278 (SPI)
// Required Libraries: LoRa by Sandeep Mistry, rc-switch by sui77

#include <SPI.h>
#include <LoRa.h>
#include <RCSwitch.h>
#include <WiFi.h>
#include <HTTPClient.h>

// SX1278 LoRa Transceiver SPI pins
#define SS      15
#define RST     14
#define DI0     2

// 433MHz RF Transceiver Pins
#define RF_RX_PIN 12
#define RF_TX_PIN 13

// WiFi Network Details
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";
const char* backendUrl = "http://YOUR_BACKEND_IP:3000";

RCSwitch mySwitch = RCSwitch();
bool rfLearningMode = false;
bool loraLearningMode = false;
double currentFrequency = 433.92;

void setup() {
  Serial.begin(115200);
  while (!Serial);

  // Initialize RF 433MHz Receiver
  mySwitch.enableReceive(digitalPinToInterrupt(RF_RX_PIN));
  mySwitch.enableTransmit(RF_TX_PIN);
  Serial.println("[RF] Receiver and Transmitter initialized.");

  // Initialize SX1278 LoRa
  SPI.begin();
  LoRa.setPins(SS, RST, DI0);
  if (!LoRa.begin(433E6)) { // Set frequency (433MHz default)
    Serial.println("[LoRa] SX1278 initialization failed.");
  } else {
    Serial.println("[LoRa] SX1278 initialized.");
  }

  // Connect to Wi-Fi
  WiFi.begin(ssid, password);
  Serial.print("[WiFi] Connecting");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\n[WiFi] Connected. IP: " + WiFi.localIP().toString());
  sendTrackLog("Transceiver Online", "ESP32_0X7FF");
}

void loop() {
  // 1. RF Ask/OOK Code Learning
  if (mySwitch.available()) {
    long value = mySwitch.getReceivedValue();
    unsigned int bitLength = mySwitch.getReceivedBitlength();
    unsigned int delayTime = mySwitch.getReceivedDelay();
    unsigned int protocol = mySwitch.getReceivedProtocol();

    if (value != 0) {
      Serial.print("RF Code Received: ");
      Serial.print(value);
      Serial.print(" | Proto: ");
      Serial.println(protocol);

      // Send to server if learning mode or connection online
      sendRfToServer(value, protocol, bitLength, delayTime);
    }
    mySwitch.resetAvailable();
  }

  // 2. LoRa Packet Listening
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    Serial.print("LoRa Packet Received: ");
    String payload = "";
    while (LoRa.available()) {
      payload += (char)LoRa.read();
    }
    Serial.println(payload);

    float snr = LoRa.packetSnr();
    int rssi = LoRa.packetRssi();
    sendLoraToServer(payload, snr, rssi);
  }

  // Check commands from server via polling or WebSocket connection
  checkServerDirectives();
  delay(10);
}

void sendRfToServer(long value, unsigned int protocol, unsigned int bits, unsigned int delayTime) {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(String(backendUrl) + "/codes/rf");
    http.addHeader("Content-Type", "application/json");
    
    String json = "{\"name\":\"RF Captured\",\"raw_code\":\"" + String(value) + 
                  "\",\"protocol\":\"" + String(protocol) + 
                  "\",\"bitLength\":" + String(bits) + 
                  ",\"delayTime\":" + String(delayTime) + "}";
    
    int code = http.POST(json);
    http.end();
  }
}

void sendLoraToServer(String payload, float snr, int rssi) {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(String(backendUrl) + "/codes/lora");
    http.addHeader("Content-Type", "application/json");
    
    String json = "{\"name\":\"LoRa Captured\",\"payload\":\"" + payload + 
                  "\",\"frequency\":433.92,\"SF\":7,\"BW\":125.0,\"CRC\":true}";
    
    int code = http.POST(json);
    http.end();
  }
}

void sendTrackLog(String eventMsg, String deviceId) {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(String(backendUrl) + "/history");
    http.addHeader("Content-Type", "application/json");
    String json = "{\"event\":\"" + eventMsg + "\",\"device_id\":\"" + deviceId + "\"}";
    http.POST(json);
    http.end();
  }
}

void checkServerDirectives() {
  // Implementation of command polling or WebSocket triggers to set learning status
}
""".trimIndent()

    val nodeBackend = """
// Node.js Express Backend & WebSockets API (Socket.io)
// dependencies: express, mongoose, socket.io, jsonwebtoken, dotenv, cors

const express = require('express');
const http = require('http');
const mongoose = require('mongoose');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const { Server } = require('socket.io');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });

app.use(cors());
app.use(express.json());

const JWT_SECRET = process.env.JWT_SECRET || 'transceiver_key_433';
const MONGO_URI = process.env.MONGO_URI || 'mongodb://localhost:27017/rflora_dashboard';

// MongoDB Schema Definitions
const RfCodeSchema = new mongoose.Schema({
  name: { type: String, required: true },
  raw_code: { type: String, required: true },
  protocol: { type: String, default: '1' },
  timestamp: { type: Date, default: Date.now }
});

const LoraCodeSchema = new mongoose.Schema({
  name: { type: String, required: true },
  payload: { type: String, required: true },
  frequency: { type: Number, default: 433.92 },
  SF: { type: Number, default: 7 },
  BW: { type: Number, default: 125 },
  CRC: { type: Boolean, default: true },
  timestamp: { type: Date, default: Date.now }
});

const HistoryLogSchema = new mongoose.Schema({
  event: { type: String, required: true },
  device_id: { type: String, required: true },
  time: { type: Date, default: Date.now }
});

const RfCode = mongoose.model('RfCode', RfCodeSchema);
const LoraCode = mongoose.model('LoraCode', LoraCodeSchema);
const HistoryLog = mongoose.model('HistoryLog', HistoryLogSchema);

// Connection Status Monitor
let learningRf = false;
let learningLora = false;

// JWT Middleware Authentication
const authenticateToken = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (!token) return res.sendStatus(401);
  
  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) return res.sendStatus(403);
    req.user = user;
    next();
  });
};

// --- API Roots & Routing ---

// Learn Control toggles and event broadcasters
app.post('/learn/rf', (req, res) => {
  learningRf = req.body.active ?? !learningRf;
  const status = learningRf ? 'RF Learning Mode Activated' : 'RF Learning Mode Terminated';
  io.emit('status_update', { type: 'rf_learning', active: learningRf });
  logEvent(status, 'dashboard_api');
  res.json({ learningRf: learningRf, msg: status });
});

app.post('/learn/lora', (req, res) => {
  learningLora = req.body.active ?? !learningLora;
  const status = learningLora ? 'Lora Learning Mode Activated' : 'Lora Learning Mode Terminated';
  io.emit('status_update', { type: 'lora_learning', active: learningLora });
  logEvent(status, 'dashboard_api');
  res.json({ learningLora: learningLora, msg: status });
});

// RF Codes (CRUD)
app.get('/codes/rf', async (req, res) => {
  try {
    const codes = await RfCode.find().sort({ timestamp: -1 });
    res.json(codes);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post('/codes/rf', async (req, res) => {
  try {
    const code = new RfCode(req.body);
    await code.save();
    io.emit('new_rf_code', code);
    await logEvent(`Captured RF Code: [${'$'}{code.raw_code}]`, 'ESP32_0X7FF');
    res.status(201).json(code);
  } catch (err) { res.status(400).json({ error: err.message }); }
});

app.put('/codes/rf/:id', async (req, res) => {
  try {
    const code = await RfCode.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json(code);
  } catch (err) { res.status(400).json({ error: err.message }); }
});

app.delete('/codes/rf/:id', async (req, res) => {
  try {
    await RfCode.findByIdAndDelete(req.params.id);
    res.json({ msg: "RF Signal entry deleted." });
  } catch (err) { res.status(400).json({ error: err.message }); }
});

// LoRa Codes (CRUD)
app.get('/codes/lora', async (req, res) => {
  try {
    const codes = await LoraCode.find().sort({ timestamp: -1 });
    res.json(codes);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post('/codes/lora', async (req, res) => {
  try {
    const code = new LoraCode(req.body);
    await code.save();
    io.emit('new_lora_code', code);
    await logEvent(`Captured LoRa packet: [${'$'}{code.payload}]`, 'ESP32_SX1278');
    res.status(201).json(code);
  } catch (err) { res.status(400).json({ error: err.message }); }
});

app.put('/codes/lora/:id', async (req, res) => {
  try {
    const code = await LoraCode.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json(code);
  } catch (err) { res.status(400).json({ error: err.message }); }
});

app.delete('/codes/lora/:id', async (req, res) => {
  try {
    await LoraCode.findByIdAndDelete(req.params.id);
    res.json({ msg: "LoRa Packet deleted from database." });
  } catch (err) { res.status(400).json({ error: err.message }); }
});

// Replay Signals
app.post('/replay/rf/:id', async (req, res) => {
  try {
    const code = await RfCode.findById(req.params.id);
    if (!code) return res.status(404).json({ error: "Code not found" });
    
    // Command ESP32 to replay signal
    io.emit('directive', { action: 'replay_rf', code: code.raw_code, protocol: code.protocol });
    await logEvent(`Triggered Replay: RF Code [${'$'}{code.raw_code}]`, 'user_dash');
    res.json({ success: true, replayed: code.raw_code });
  } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post('/replay/lora/:id', async (req, res) => {
  try {
    const packet = await LoraCode.findById(req.params.id);
    if (!packet) return res.status(404).json({ error: "Packet entry not found" });
    
    // Command ESP32 transceiver to re-transmit
    io.emit('directive', { action: 'transmit_lora', payload: packet.payload, SF: packet.SF, BW: packet.BW });
    await logEvent(`Triggered Re-transmit: LoRa packet [${'$'}{packet.payload}]`, 'user_dash');
    res.json({ success: true, retransmitted: packet.payload });
  } catch (err) { res.status(500).json({ error: err.message }); }
});

// Connections History Events Channel
app.get('/history', async (req, res) => {
  try {
    const logs = await HistoryLog.find().sort({ time: -1 }).limit(100);
    res.json(logs);
  } catch (err) { res.status(500).json({ error: err.message }); }
});

app.post('/history', async (req, res) => {
  try {
    const log = new HistoryLog({ event: req.body.event, device_id: req.body.device_id });
    await log.save();
    io.emit('log_update', log);
    res.status(201).json(log);
  } catch (err) { res.status(400).json({ error: err.message }); }
});

async function logEvent(eventText, deviceId) {
  try {
    const log = new HistoryLog({ event: eventText, device_id: deviceId });
    await log.save();
    io.emit('log_update', log);
    console.log(`[LOG] ${'$'}{eventText} from ${'$'}{deviceId}`);
  } catch (e) {
    console.error("Logger error: ", e);
  }
}

// Websocket Events Orchestrator
io.on('connection', (socket) => {
  console.log('Client dashboard connected: ' + socket.id);
  socket.emit('device_state', { learningRf, learningLora });
  
  socket.on('disconnect', () => {
    console.log('Client dashboard disconnected: ' + socket.id);
  });
});

mongoose.connect(MONGO_URI)
  .then(() => {
    server.listen(3000, () => {
      console.log('=== Server running on port 3000 ===');
    });
  })
  .catch(err => console.error("Database connection failed", err));
""".trimIndent()

    val dbSchemaDoc = """
-- PostgreSQL Relational Schema & Indices (Optional relational DB variant)

CREATE TABLE rf_codes (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    raw_code VARCHAR(1024) NOT NULL,
    protocol VARCHAR(50) DEFAULT '1',
    frequency DOUBLE PRECISION DEFAULT 433.92,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lora_codes (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    frequency DOUBLE PRECISION DEFAULT 433.92,
    sf INT DEFAULT 7,
    bw DOUBLE PRECISION DEFAULT 125.0,
    crc BOOLEAN DEFAULT TRUE,
    rssi INT DEFAULT -55,
    snr DOUBLE PRECISION DEFAULT 8.5,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE connection_logs (
    id SERIAL PRIMARY KEY,
    event TEXT NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index optimizations for lightning-fast searches on Code Names and Protocols
CREATE INDEX idx_rf_codes_name_proto ON rf_codes (name, protocol);
CREATE INDEX idx_lora_codes_name_payload ON lora_codes (name);
CREATE INDEX idx_connection_logs_time ON connection_logs (timestamp DESC);
""".trimIndent()

    val setupGuide = """
=== CORE SETUP GUIDE FOR DIGITAL FREQUENCY WORKSTATION ===

1. ESP32 Hardware Wiring Config:
   [1] RX433 ASK Receiver:
       - VCC  -> ESP32 5V (or 3.3V)
       - GND  -> ESP32 GND
       - DATA -> ESP32 GPIO 12/G12 (with hardware interrupt)
   [2] TX433 ASK Transmitter (Replay Node):
       - VCC  -> ESP32 5V
       - GND  -> ESP32 GND
       - DATA -> ESP32 GPIO 13/G13
   [3] SX1276/78 SPI transceiver:
       - VCC  -> 3.3V (Strict! 5V will burn SX1278)
       - GND  -> GND
       - MISO -> GPIO 19
       - MOSI -> GPIO 23
       - SCK  -> GPIO 18
       - NSS  -> GPIO 15
       - RST  -> GPIO 14
       - DIO0 -> GPIO 2 (Interrupt trigger packet rx)

2. Backend Server Deployment:
   - Make a directory `backend` and run `npm init -y`
   - Run `npm install express mongoose socket.io jsonwebtoken dotenv cors`
   - Save the Node.js source from the app's copy console into `index.js`
   - Configure a `.env` file with `MONGO_URI` and `JWT_SECRET`
   - Start database with `mongod` and launch express with `node index.js`
   
3. ESP32 Integration:
   - Open Arduino IDE. Add ESP32 core via Preferences boards manager.
   - Install "LoRa" library (by Sandeep Mistry) and "rc-switch" (by sui77).
   - Paste the ESP32 Sketch provided.
   - Adjust `YOUR_WIFI_SSID`, `YOUR_WIFI_PASSWORD`, and `backendUrl` with your networks.
   - Compile and flash! Enjoy controlling your receivers and transceiver nodes in modern visualizer graphs!
""".trimIndent()
}
