import React from "react";

interface JsonPanelProps {
  data: unknown;
}

export function JsonPanel({ data }: JsonPanelProps) {
  if (data === undefined || data === null) {
    return null;
  }

  return <pre className="json-block">{JSON.stringify(data, null, 2)}</pre>;
}
