# AI Call Guard - Server

## 1. Twilio
1. twilio.com par account banao, ek **Voice** wala number lo (India number ke liye KYC documents lagte hain).
2. Trial account mein sirf verified numbers par call ja sakti hai. Console → Verified Caller IDs mein apna asli number verify karo, ya account upgrade karo.
3. Console se **Auth Token** copy karo.

## 2. Server deploy (Render.com)
1. Is repo ko GitHub par rakho. Render par **New → Web Service** → apna repo chuno.
2. **Root Directory:** `server` | **Build:** `npm install` | **Start:** `npm start`
3. Environment variables mein `.env.example` ki saari values bharo. `PUBLIC_URL` apne Render URL ke barabar rakho (aakhir mein `/` nahi).
4. Free plan 15 minute baad so jata hai, aur so-ya server call nahi utha payega. Do raaste:
   - uptimerobot.com par `https://APP.onrender.com/health` ko har 5 minute ping karwao, ya
   - paid plan lo.

## 3. Twilio number set karo
Number ki settings mein:
- **A call comes in** → Webhook → `https://APP.onrender.com/voice` → HTTP POST
- **Call status changes** → `https://APP.onrender.com/status` → HTTP POST

## 4. Test
Pehle kisi doosre phone se seedha Twilio number par call karo. AI baat karega, "3050" bolne par tumhare phone par call aayegi.

## 5. Phone se forwarding
Test sahi chale to AI Call Guard app mein Twilio number daalo aur forwarding ON karo.
