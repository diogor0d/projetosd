# Googol API - Postman Collection

This folder contains Postman collections for testing the Googol distributed search engine API.

## Import Instructions

1. Open Postman
2. Click **Import** button
3. Select `Googol-API.postman_collection.json`
4. The collection will appear in your Collections sidebar

## Available Endpoints

### Search

- **Search - Page 0**: Basic search with pagination
- **Search - Multiple Terms**: Search with multiple query terms

### Indexing

- **Index URL**: Submit a single URL for high-priority indexing
- **Index HackerNews Stories**: Index top HackerNews stories containing specific terms
  - `query`: Search terms to filter stories (e.g., "python", "rust", "AI")
  - `maxUrls`: Number of stories to index (default: 10)

### HackerNews API

- **List HackerNews Stories**: Returns the top stories whose titles contain the requested terms
  - `query`: Space-separated keywords used to filter the title text
  - `maxStories`: How many stories to inspect before stopping (default: 10, max 200)
- **Index HackerNews Stories (API)**: Submits the same filter via JSON so you can trigger indexing from scripts
  - Request body:
    ```json
    {
      "query": "distributed systems",
      "maxUrls": 20
    }
    ```
  - This endpoint responds immediately with `202 Accepted` and runs the indexing job in the background

### System Stats

- **Get System Stats**: View system statistics including indexed pages, active barrels, and top searches

### Page Analysis

- **Get Incoming Links**: Find all pages that link to a specific URL

### AI Features

- **AI Analysis**: Generate contextual analysis of search results (requires OpenAI API key)
- **Spell Check**: Get spelling suggestions for query terms

### OpenRouter

- **OpenRouter - Test Prompt**: Validates connectivity to the OpenRouter AI service
  - Request body:
    ```json
    {
      "prompt": "Summarize the state of distributed search engines in 3 sentences.",
      "maxTokens": 200
    }
    ```
  - Use this to confirm the backend can reach the OpenRouter endpoint and retrieve a textual response

## Testing HackerNews Indexing

To test HackerNews story indexing:

1. Select the **Index HackerNews Stories** request
2. In the **Body** tab, modify the parameters:
   - `query`: Enter search terms (e.g., "javascript", "blockchain", "machine learning")
   - `maxUrls`: Set how many stories to index (1-100 recommended)
3. Click **Send**
4. Check the Spring Boot console to see stories being indexed:
   ```
   [HackerNews] Indexing: Story Title (https://story-url.com)
   ```
5. After indexing completes, use the **Search** endpoint to find the indexed content

Alternatively, the **HackerNews API → Index HackerNews Stories (API)** request lets you post the same payload as JSON, which is useful when scripting the call or triggering from CI.

## Configuration

The default base URL is `http://localhost:8081`. If your application runs on a different port, update the collection variable:

1. Click on the collection name
2. Go to **Variables** tab
3. Update the `baseUrl` value

## Notes

- HackerNews indexing is asynchronous - it returns immediately and processes in the background
- High-priority URLs (user-submitted and HackerNews) are processed before crawled URLs
- Search results include TF-IDF scores for relevance ranking
- AI features require openrouterAI API key configuration in `application.properties`
