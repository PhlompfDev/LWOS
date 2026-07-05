# M3 Bug Fixes Implementation Plan

## Issue 1: Preview Block Obscuring (Z-Fighting & Height)
In the M3 branch, translucent preview blocks are rendered at the exact same bounding box as the existing real blocks. This causes them to z-fight on the sides. Additionally, since path blocks are 1 pixel shorter than full blocks (15/16 height), they are rendered inside the existing terrain block, making their top texture invisible.

**Proposed Fix:**
Apply a slight translation and scale to the `PoseStack` before rendering the preview blocks. By translating the Y axis up by 2 pixels (`0.125f`), the top face of the path block will emerge visibly above the full block.

1. **Modify `PreviewRenderer.java`**:
   - Locate the rendering loop inside `PreviewRenderer.render`.
   - Update the `PoseStack` transformations right after `ps.translate`:

```java
ps.pushPose();
ps.translate(pos.x(), pos.y(), pos.z());

// ADD THESE TWO LINES:
// Offset Y by 2 pixels (0.125) to clear the top of full blocks,
// and apply a tiny scale/xz-offset to prevent side z-fighting.
ps.translate(-0.005f, 0.125f, -0.005f);
ps.scale(1.01f, 1.01f, 1.01f);

BakedModel model = blockRenderer.getBlockModel(state);
// ... renderModel call ...
ps.popPose();
```

## Issue 2: Paths Generating on Leaves/Canopies
Currently, paths build over trees (turning leaves into dirt paths) instead of following the ground terrain under the trees. This happens because `ForgeWorldView` and `ServerWorldView` use the `MOTION_BLOCKING` heightmap, which stops at leaves, and the client does not maintain `MOTION_BLOCKING_NO_LEAVES`.

**Proposed Fix:**
Implement a deterministic downward scan in both `ForgeWorldView` and `ServerWorldView` starting from the `MOTION_BLOCKING` height. The scan should skip over transparent or foliage blocks (like leaves and logs) until it hits solid ground.

1. **Modify `ForgeWorldView.java` and `ServerWorldView.java`**:
   - Update the `surfaceHeight(int x, int z)` method to scan downwards.
   - Example logic to implement in both views to maintain determinism:

```java
@Override
public int surfaceHeight(int x, int z) {
    Level level = ... // get level
    if (level == null) return 64;
    
    // Start at the motion-blocking height
    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    
    // Scan downwards to skip leaves and logs
    BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(x, y, z);
    while (y > level.getMinBuildHeight()) {
        BlockState state = level.getBlockState(mutablePos);
        // Using block tags or instance checks to bypass trees
        if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && state.blocksMotion()) {
            break;
        }
        y--;
        mutablePos.setY(y);
    }
    return y;
}
```

*Note on Performance: A known performance issue with the preview mesh rendering was reported. However, according to the M3 Design Spec, large-scale preview performance is a recognized engineering risk with mitigations planned (chunk-section batching and debounced rebuilds) for a later milestone. Therefore, performance optimization is excluded from this immediate bug fix.*
