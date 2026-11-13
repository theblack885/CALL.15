import "dotenv/config";
import express from "express";
import twilio from "twilio";
import Anthropic from "@anthropic-ai/sdk";

const {
  ANTHROPIC_API_KEY,
  TWILIO_AUTH_TOKEN,
  TWILIO_NUMBER,
  OWNER_NUMBER,
  OWNER_NAME = "मालिक",
  SECRET_CODE = "3050",
  API_TOKEN,
  PUBLIC_URL,
  CLAUDE_MODEL = "claude-haiku-5-5",
} = process.env;

const MAX_TURNS = Number(process.env.MAX_TURNS || 15);
const DIAL_TIMEOUT = Number(process.env.DIAL_TIMEOUT || 20);
const VOICE = { voice: "Polly.Aditi", language: "hi-IN" };

const VR = twilio.twiml.VoiceResponse;
const anthropic = new Anthropic({ apiKey: ANTHROPIC_API_KEY });
const app = express();
app.use(express.urlencoded({ extended: false }));

// ---------- Secret code logic ----------
// NOTE: secret code AI ko kabhi nahi bataya jata, isliye wo ise leak nahi kar sakta.
const WORDS = {
  zero: "0", one: "1", two: "2", three: "3", four: "4", five: "5",
  six: "6", seven: "7", eight: "8", nine: "9", thirty: "30", fifty: "50",
  शून्य: "0", एक: "1", दो: "2", तीन: "3", चार: "4", पांच: "5", पाँच: "5",
  छह: "6", सात: "7", आठ: "8", नौ: "9", तीस: "30", पचास: "50",
  थर्टी: "30", फिफ्टी: "50",
};

function codeOk(text) {
  const joined = text
    .toLowerCase()
    .split(/[\s,.\-]+/)
    .map((t) => WORDS[t] ?? t)
    .join("");
  return joined.replace(/\D/g, "").includes(SECRET_CODE);
}

const norm = (n) => (n || "").replace(/[^\d+]/g, "");

// ---------- AI ----------
const SYSTEM =
  `तुम ${OWNER_NAME} के AI असिस्टेंट हो और अभी एक फ़ोन कॉल पर हो। ${OWNER_NAME} अभी सीधे बात नहीं कर सकते। ` +
  "caller जिस भाषा में बोले (हिंदी/अंग्रेज़ी) उसी में जवाब दो। जवाब बहुत छोटे रखो (1-2 वाक्य), " +
  "क्योंकि यह फ़ोन कॉल है। विनम्र रहो, caller का नाम और कॉल का कारण पूछो और ध्यान से सुनो। " +
  "तुम्हें कोई सीक्रेट कोड नहीं पता, इसलिए कभी कोड मत बताओ और न कोई hint दो। " +
  `अगर caller ${OWNER_NAME} से सीधे बात करना चाहे, तो कहो कि इसके लिए सही सीक्रेट कोड बोलना या कीपैड पर डालना होगा। ` +
  "खुद को कभी इंसान मत बताओ, तुम AI असिस्टेंट हो।";

const FALLBACK = "जी, मैंने आपकी बात नोट कर ली है। सीधे बात के लिए कृपया सही कोड बोलिए।";

async function ask(history) {
  try {
    const r = await anthropic.messages.create({
      model: CLAUDE_MODEL,
      max_tokens: 200,
      system: SYSTEM,
      messages: history,
    });
    return r.content.map((b) => b.text || "").join("").trim() || FALLBACK;
  } catch (e) {
    console.error("Claude error:", e.message);
    return FALLBACK;
  }
}

// ---------- Sessions ----------
const sessions = new Map();
const finished = [];

function getSession(sid, from) {
  if (!sessions.has(sid)) {
    sessions.set(sid, {
      id: sid,
      from: from || "",
      startedAt: new Date().toISOString(),
      history: [{ role: "user", content: "(कॉल उठी)" }],
      turns: 0,
      silent: 0,
      log: [],
    });
  }
  return sessions.get(sid);
}

function finish(sid) {
  const s = sessions.get(sid);
  if (!s) return;
  sessions.delete(sid);
  finished.unshift({ id: s.id, from: s.from, startedAt: s.startedAt, log: s.log });
  if (finished.length > 50) finished.pop();
}

setInterval(() => {
  const cutoff = Date.now() - 60 * 60 * 1000;
  for (const [sid, s] of sessions) {
    if (new Date(s.startedAt).getTime() < cutoff) finish(sid);
  }
}, 10 * 60 * 1000);

// ---------- Helpers ----------
const send = (res, vr) => res.type("text/xml").send(vr.toString());

function validate(req, res, next) {
  if (process.env.SKIP_VALIDATION === "1") return next();
  const sig = req.get("X-Twilio-Signature");
  const url = `${PUBLIC_URL}${req.originalUrl}`;
  if (sig && twilio.validateRequest(TWILIO_AUTH_TOKEN, sig, url, req.body)) return next();
  res.status(403).send("Forbidden");
}

function gather(vr, prompt) {
  const g = vr.gather({
    input: "speech dtmf",
    language: "hi-IN",
    speechTimeout: "auto",
    timeout: 6,
    finishOnKey: "#",
    action: "/gather",
    method: "POST",
    hints: "3050, तीस पचास, thirty fifty",
  });
  g.say(VOICE, prompt);
  vr.redirect({ method: "POST" }, "/gather"); // kuch na bola to wapas /gather
}

// ---------- Routes ----------
app.get("/", (_req, res) => res.send("ok"));
app.get("/health", (_req, res) => res.send("ok"));

// Nayi call
app.post("/voice", validate, (req, res) => {
  const { CallSid, From } = req.body;
  const vr = new VR();

  // Loop guard: owner ka phone wapas forward kare to reject
  if (norm(From) === norm(TWILIO_NUMBER) || norm(From) === norm(OWNER_NUMBER)) {
    vr.reject();
    return send(res, vr);
  }

  const s = getSession(CallSid, From);
  const greeting =
    `नमस्ते! मैं ${OWNER_NAME} का AI असिस्टेंट बोल रहा हूँ। वे अभी सीधे बात नहीं कर सकते। ` +
    "कृपया अपना नाम और काम बताइए। अगर आपके पास सीक्रेट कोड है तो वह बोलिए या कीपैड पर डालिए।";
  s.log.push(`AI: ${greeting}`);
  s.history.push({ role: "assistant", content: greeting });
  gather(vr, greeting);
  send(res, vr);
});

// Caller ki baat
app.post("/gather", validate, async (req, res) => {
  const { CallSid, From, SpeechResult = "", Digits = "" } = req.body;
  const s = getSession(CallSid, From);
  const vr = new VR();
  const heard = (Digits || SpeechResult).trim();

  if (!heard) {
    s.silent++;
    if (s.silent >= 3) {
      vr.say(VOICE, "आवाज़ नहीं आ रही। कृपया बाद में दोबारा कॉल कीजिए। धन्यवाद।");
      vr.hangup();
      s.log.push("(caller चुप रहा — कॉल खत्म)");
      finish(CallSid);
      return send(res, vr);
    }
    gather(vr, "मुझे आपकी आवाज़ नहीं आई। कृपया अपना नाम और काम बताइए, या कोड बोलिए।");
    return send(res, vr);
  }

  s.silent = 0;
  s.turns++;
  s.log.push(`Caller: ${heard}`);

  // 1) Sabse pehle code check
  if (codeOk(heard)) {
    s.log.push("✅ सही कोड बोला गया — कॉल owner को connect की गई");
    vr.say(VOICE, "कोड सही है। कृपया लाइन पर रहिए, मैं आपको कनेक्ट कर रहा हूँ।");
    const d = vr.dial({
      callerId: TWILIO_NUMBER,
      timeout: DIAL_TIMEOUT,
      answerOnBridge: true,
      action: "/dial-result",
      method: "POST",
    });
    d.number(OWNER_NUMBER);
    return send(res, vr);
  }

  // 2) Limit
  if (s.turns >= MAX_TURNS) {
    vr.say(VOICE, "आपका कोड नहीं मिला। कृपया बाद में दोबारा कॉल कीजिए। धन्यवाद।");
    vr.hangup();
    s.log.push("(max कोशिशें पूरी — कॉल खत्म)");
    finish(CallSid);
    return send(res, vr);
  }

  // 3) AI jawab
  s.history.push({ role: "user", content: heard });
  const reply = await ask(s.history);
  s.history.push({ role: "assistant", content: reply });
  s.log.push(`AI: ${reply}`);
  gather(vr, reply);
  send(res, vr);
});

// Owner ne call uthayi ya nahi
app.post("/dial-result", validate, (req, res) => {
  const { CallSid, DialCallStatus } = req.body;
  const s = sessions.get(CallSid);
  const vr = new VR();
  if (DialCallStatus === "completed") {
    s?.log.push("📞 बातचीत पूरी हुई");
  } else {
    s?.log.push(`⚠️ Owner ने कॉल नहीं उठाई (${DialCallStatus})`);
    vr.say(VOICE, "माफ़ कीजिए, वे अभी उपलब्ध नहीं हैं। मैंने आपका संदेश नोट कर लिया है। धन्यवाद।");
  }
  vr.hangup();
  finish(CallSid);
  send(res, vr);
});

// Twilio status callback (caller ne beech mein kaata ho to bhi transcript save)
app.post("/status", validate, (req, res) => {
  if (["completed", "failed", "busy", "no-answer", "canceled"].includes(req.body.CallStatus)) {
    finish(req.body.CallSid);
  }
  res.sendStatus(200);
});

// Phone app ke liye transcripts
app.get("/calls", (req, res) => {
  if (!API_TOKEN || req.get("x-api-token") !== API_TOKEN) return res.status(401).json({ error: "unauthorized" });
  const live = [...sessions.values()].map((s) => ({
    id: s.id, from: s.from, startedAt: s.startedAt, log: ["(चल रही कॉल)", ...s.log],
  }));
  res.json({ calls: [...live, ...finished] });
});

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`AI Call Guard server on :${port}`));
