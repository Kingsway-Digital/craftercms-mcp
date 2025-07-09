<#import "/templates/system/common/crafter.ftl" as crafter />

<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<title>${contentModel.title_t}</title>
	<style>
            html, body {
                color: #333;
                height: 100%;
                background: #f3f3f3;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
            }

            main {
                max-width: 800px;
                padding: 40px;
                background: rgba(255, 255, 255, 0.6);
                border-radius: 20px;
                margin: 100px auto;
            }

            .chat-container {
                margin-top: 20px;
                background: rgba(255, 255, 255, 0.9);
                border-radius: 10px;
                padding: 20px;
                box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
            }

            .chat-messages {
                max-height: 300px;
                overflow-y: auto;
                margin-bottom: 10px;
                padding: 10px;
                border: 1px solid #ddd;
                border-radius: 5px;
                background: #fff;
            }

            .chat-message {
                margin: 5px 0;
                padding: 8px;
                border-radius: 5px;
            }

            .chat-message.user {
                background: #e0f7fa;
                text-align: right;
            }

            .chat-message.bot {
                background: #f5f5f5;
                text-align: left;
            }

            .chat-input {
                display: flex;
                gap: 10px;
            }

            .chat-input input {
                flex: 1;
                padding: 10px;
                border: 1px solid #ddd;
                border-radius: 5px;
                font-size: 14px;
            }

            .chat-input button {
                padding: 10px 20px;
                border: none;
                background: #007bff;
                color: white;
                border-radius: 5px;
                cursor: pointer;
                font-size: 14px;
            }

            .chat-input button:hover {
                background: #0056b3;
            }
	</style>
    <@crafter.head/>
</head>
<body>
<@crafter.body_top/>
<main>
    <@crafter.h1 $field="title_t">${contentModel.title_t}</@crafter.h1>
    <@crafter.div $field="body_html">${contentModel.body_html}</@crafter.div>
    
    <div class="chat-container">
        <div class="chat-messages" id="chatMessages"></div>
        <div class="chat-input">
            <input type="text" id="chatInput" placeholder="Type your message..." />
            <button onclick="sendMessage()">Send</button>
        </div>
    </div>
</main>
<@crafter.body_bottom/>
<script>
    async function sendMessage() {
        const input = document.getElementById('chatInput');
        const messages = document.getElementById('chatMessages');
        const message = input.value.trim();

        if (!message) return;

        // Display user message
        const userMessage = document.createElement('div');
        userMessage.className = 'chat-message user';
        userMessage.textContent = message;
        messages.appendChild(userMessage);
        input.value = '';
        messages.scrollTop = messages.scrollHeight;

        try {
            // Replace '/api/chat' with your actual chat service endpoint
            const response = await fetch('/api/chat/complete.json', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ message }),
            });

            if (!response.ok) throw new Error('Chat service error');

            const data = await response.json();
            const botMessage = document.createElement('div');
            botMessage.className = 'chat-message bot';
            botMessage.textContent = data.response || 'No response from chat service';
            messages.appendChild(botMessage);
            messages.scrollTop = messages.scrollHeight;
        } catch (error) {
            const errorMessage = document.createElement('div');
            errorMessage.className = 'chat-message bot';
            errorMessage.textContent = 'Error: ' + error.message;
            messages.appendChild(errorMessage);
            messages.scrollTop = messages.scrollHeight;
        }
    }

    // Allow sending message with Enter key
    document.getElementById('chatInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });
</script>
</body>
</html>