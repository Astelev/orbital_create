package io.github.astelev.cruising.compat.dh.progressive;

import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalResult;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import io.github.astelev.cruising.compat.dh.progressive.ProgressiveDiagnostics;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongFunction;

/** Native adapter stays outside mixin classes; source ownership remains entirely with DH. */
public final class DhProgressiveBackend {
    public static ProgressiveRequests<DataSourceRetrievalResult> create(GeneratedFullDataSourceProvider provider,
            LongFunction<CompletableFuture<DataSourceRetrievalResult>> enqueue,int limit) {
        return new ProgressiveRequests<>(new ProgressiveRequests.Backend<>() {
            @Override public boolean ready(long pos) {
                var missing=provider.getPositionsToRetrieve(pos,DhSectionPos.getDetailLevel(pos));
                return missing!=null && missing.isEmpty();
            }
            @Override public CompletableFuture<DataSourceRetrievalResult> enqueue(long pos) {
                ProgressiveDiagnostics.count("server","progressive.stage_submit."+(DhSectionPos.getDetailLevel(pos)-6));
                long start=ProgressiveDiagnostics.start();
                var result=enqueue.apply(pos);
                if(result!=null)result.whenComplete((r,e)->{
                    ProgressiveDiagnostics.time("server","progressive.queue_to_result."+(DhSectionPos.getDetailLevel(pos)-6),start);
                    if(e!=null)ProgressiveDiagnostics.count("server","progressive.failed");
                });
                return result;
            }
        },limit);
    }
    private DhProgressiveBackend(){}
}
