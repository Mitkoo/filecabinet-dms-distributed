# react-files

The FileCabinet web frontend. Vite + React 19 + TypeScript, running on port 5173.

## Features

Login and registration, document dashboard with paging, upload, a document detail view
with the extracted fields and a manual field editor, review inbox and workflow detail with
approve/reject actions, profile page and user administration.

## Run

```
npm install
npm run dev
```

The dev server proxies `/api` to the main app on http://localhost:8081.
JWT is stored in the browser and attached to every request.

## Build

```
npm run build
```
