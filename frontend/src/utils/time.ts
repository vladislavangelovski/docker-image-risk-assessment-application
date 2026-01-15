export function formatRelativeTime(timestampMs: number): string {
  const diffMs = Date.now() - timestampMs;
  const abs = Math.abs(diffMs);
  const minutes = Math.floor(abs / 60000);
  const hours = Math.floor(abs / 3600000);
  const days = Math.floor(abs / 86400000);

  const suffix = diffMs >= 0 ? "ago" : "from now";

  if (abs < 15000) return "Just now";
  if (minutes < 1) return `Less than a minute ${suffix}`;
  if (minutes < 60) return `${minutes}m ${suffix}`;
  if (hours < 24) return `${hours}h ${suffix}`;
  return `${days}d ${suffix}`;
}

