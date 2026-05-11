interface ClippingLogoProps {
  size?: number;
  className?: string;
}

export function ClippingLogo({ size = 24, className }: ClippingLogoProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" className={className}>
      {/* ambient glow */}
      <circle cx="24" cy="24" r="20" fill="url(#clippingGlow)" opacity="0.25" />
      {/* handle */}
      <path d="M20 10 Q24 6 28 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" fill="none" />
      {/* top plate */}
      <path d="M17 12 L31 12" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
      {/* body */}
      <rect x="17" y="14" width="14" height="20" rx="4" stroke="currentColor" strokeWidth="1.5" fill="none" opacity="0.4" />
      {/* inner glow */}
      <rect x="19" y="16" width="10" height="16" rx="3" fill="url(#clippingInner)" />
      {/* center bright */}
      <circle cx="24" cy="24" r="3" fill="var(--clipping-center, #FFF5D6)" opacity="0.8" />
      {/* bottom plate */}
      <path d="M17 36 L31 36" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
      {/* light rays */}
      <line x1="13" y1="24" x2="15" y2="24" stroke="currentColor" strokeWidth="1" opacity="0.3" strokeLinecap="round" />
      <line x1="33" y1="24" x2="35" y2="24" stroke="currentColor" strokeWidth="1" opacity="0.3" strokeLinecap="round" />
      <defs>
        <radialGradient id="clippingGlow">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.5" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
        </radialGradient>
        <radialGradient id="clippingInner" cx="0.5" cy="0.5" r="0.5">
          <stop offset="0%" stopColor="var(--clipping-center, #FFF5D6)" stopOpacity="0.7" />
          <stop offset="50%" stopColor="currentColor" stopOpacity="0.4" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.1" />
        </radialGradient>
      </defs>
    </svg>
  );
}
