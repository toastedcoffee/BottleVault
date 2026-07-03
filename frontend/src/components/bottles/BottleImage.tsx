import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Wine } from 'lucide-react';
import { bottlesApi } from '../../api/bottles.api';

interface BottleImageProps {
  bottleId: string;
  hasImage: boolean;
  className?: string;
  alt?: string;
}

// The image endpoint requires an Authorization header, so a plain <img src>
// won't work. We fetch the bytes through the axios client (which carries the
// JWT via interceptor) and render the response as a blob URL.
export default function BottleImage({ bottleId, hasImage, className, alt }: BottleImageProps) {
  const { data: blob } = useQuery({
    queryKey: ['bottle-image', bottleId],
    queryFn: () => bottlesApi.fetchImageBlob(bottleId),
    enabled: hasImage,
    staleTime: 5 * 60 * 1000,
  });

  const [url, setUrl] = useState<string | null>(null);

  // An object URL is an external browser resource that must be created and
  // explicitly revoked, so this is a legitimate effect (exactly the "sync with
  // an external system" case effects are for) rather than derived render state —
  // there's no way to produce a revocable URL without setState here. The
  // react-hooks/set-state-in-effect heuristic doesn't account for this, so it's
  // scoped-disabled with intent.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!blob) {
      setUrl(null);
      return;
    }
    const objectUrl = URL.createObjectURL(blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);
  /* eslint-enable react-hooks/set-state-in-effect */

  if (!hasImage || !url) {
    return (
      <div
        className={`flex items-center justify-center bg-gray-100 text-gray-400 ${className ?? ''}`}
      >
        <Wine className="w-1/3 h-1/3" />
      </div>
    );
  }

  return <img src={url} alt={alt ?? 'Bottle'} className={className} />;
}
