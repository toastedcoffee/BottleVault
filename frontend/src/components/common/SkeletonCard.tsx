function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`bg-gray-200 rounded animate-pulse ${className}`} />;
}

function SkeletonCardItem() {
  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
      <div className="p-4">
        <div className="flex justify-between items-start mb-2">
          <div className="flex-1 min-w-0">
            <Skeleton className="h-3 w-20 mb-2" />
            <Skeleton className="h-4 w-36" />
          </div>
          <Skeleton className="h-5 w-16 rounded-full" />
        </div>

        <div className="flex items-center gap-3 mt-3">
          <Skeleton className="h-5 w-16 rounded" />
          <Skeleton className="h-3 w-12" />
          <Skeleton className="h-3 w-10" />
        </div>

        <div className="flex items-center gap-4 mt-3">
          <Skeleton className="h-3 w-10" />
          <Skeleton className="h-3 w-14" />
        </div>
      </div>

      <div className="border-t border-gray-100 px-4 py-2 flex justify-between items-center">
        <Skeleton className="h-4 w-8" />
        <Skeleton className="h-4 w-10" />
      </div>
    </div>
  );
}

export default function SkeletonCard({ count = 6 }: { count?: number }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      {Array.from({ length: count }, (_, i) => (
        <SkeletonCardItem key={i} />
      ))}
    </div>
  );
}
