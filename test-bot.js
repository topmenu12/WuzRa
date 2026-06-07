import TelegramBot from "node-telegram-bot-api";

const token = "8889842606:AAHr2f9l1y7LE95f9jfMwH8s60b1XYjefgU";
const chatId = "8875144289";

const bot = new TelegramBot(token, { polling: true });

bot.on("message", (msg) => {
    console.log("Received:", msg.text);
    if (msg.chat.id.toString() === chatId) {
        bot.sendMessage(msg.chat.id, "✅ البوت يعمل!");
    }
});

console.log("Bot started (test mode)");
