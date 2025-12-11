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

### System Stats

- **Get System Stats**: View system statistics including indexed pages, active barrels, and top searches

### Page Analysis

- **Get Incoming Links**: Find all pages that link to a specific URL

### AI Features

- **AI Analysis**: Generate contextual analysis of search results (requires OpenAI API key)
- **Spell Check**: Get spelling suggestions for query terms

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

## Example Queries for HackerNews

Popular terms that usually have good results:

- `python`
- `rust`
- `AI` or `artificial intelligence`
- `distributed systems`
- `kubernetes`
- `machine learning`
- `blockchain`
- `javascript`
- `golang`

## Configuration

The default base URL is `http://localhost:8081`. If your application runs on a different port, update the collection variable:

1. Click on the collection name
2. Go to **Variables** tab
3. Update the `baseUrl` value

## Notes

- HackerNews indexing is asynchronous - it returns immediately and processes in the background
- High-priority URLs (user-submitted and HackerNews) are processed before crawled URLs
- Search results include TF-IDF scores for relevance ranking
- AI features require OpenAI API key configuration in `application.properties`
