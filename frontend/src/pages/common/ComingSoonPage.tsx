export function ComingSoonPage({ label }: { label: string }) {
  return (
    <div className="flex flex-col items-center justify-center min-h-[50vh] gap-3 text-muted-foreground">
      <p className="text-lg font-medium">{label}</p>
      <p className="text-sm">준비 중이에요</p>
    </div>
  );
}
