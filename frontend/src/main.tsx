import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import QueryProvider from "@/lib/QueryProvider";
import { ErrorBoundary } from "@/components/shared/ErrorBoundary";
import { initSentryFromEnv } from "@/lib/sentry";
import "@/app/globals.css";

initSentryFromEnv();

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <QueryProvider>
      <ErrorBoundary>
        <App />
      </ErrorBoundary>
    </QueryProvider>
  </React.StrictMode>
);
