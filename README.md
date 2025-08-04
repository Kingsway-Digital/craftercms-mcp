# MCP Client / Server Implementation in CrafterCMS

## Overview
This project demonstrates how to create a MCP server using CrafterCMS and expose it to the world, as well as how to connect a MCP client to a LLM. This prototype is hard-coded to OpenAI 4o mini but implementing another option is a possibility.

## Installation & Configuration

1. Install the project
2. Create an environment variable called `crafter_chatgpt` which is the API key for OpenAI 4o mini (one day this name will be changed to align with what it's actually the key for, but today is not that day)
3. Create a Crafter Preview token and set it in `/config/engine/site-config.xml` as `site.ai.crafterPreviewToken`. Don't forget to encrypt it!
4. 