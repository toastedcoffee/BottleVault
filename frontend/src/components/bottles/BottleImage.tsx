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

  useEffect(() => {
    if (!blob) {
      setUrl(null);
      return;
    }
    const objectUrl = URL.createObjectURL(blob);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [blob]);

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
