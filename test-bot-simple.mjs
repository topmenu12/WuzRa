import TelegramBot from "node-telegram-bot-api";

const token = "8889842606:AAHr2f9l1y7LE95f9jfMwH8s60b1XYjefgU";
const bot = new TelegramBot(token, { polling: true });

bot.on("message", (msg) => {
    console.log("Received:", msg.text);
    bot.sendMessage(msg.chat.id, "Echo: " + msg.text);
});

console.log("Bot started");
