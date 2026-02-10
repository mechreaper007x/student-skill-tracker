import {
    AngularNodeAppEngine,
    createNodeRequestHandler,
    isMainModule,
    writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

// Backend URL from environment or default to local for development
const BACKEND_URL = process.env['BACKEND_URL'] || 'http://localhost:8082';

/**
 * Proxy /api requests to the Spring Boot backend
 */
app.use('/api', (req, res) => {
  const targetUrl = `${BACKEND_URL}/api${req.url}`;
  
  // Basic manual proxy for Express
  fetch(targetUrl, {
    method: req.method,
    headers: req.headers as any,
    body: req.method !== 'GET' && req.method !== 'HEAD' ? (req as any).body : undefined,
  })
    .then(async (backendRes) => {
      res.status(backendRes.status);
      backendRes.headers.forEach((value, key) => res.header(key, value));
      const data = await backendRes.arrayBuffer();
      res.send(Buffer.from(data));
    })
    .catch((err) => {
      console.error('Proxy error:', err);
      res.status(502).send('Internal Backend Error');
    });
});

/**
 * Example Express Rest API endpoints can be defined here.
 * Uncomment and define endpoints as necessary.
 *
 * Example:
 * ```ts
 * app.get('/api/{*splat}', (req, res) => {
 *   // Handle API request
 * });
 * ```
 */

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
