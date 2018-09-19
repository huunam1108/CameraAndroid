package namnh.com.cameraandroid.demo;

import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public class GridSpacingDecoration extends RecyclerView.ItemDecoration {
    private int left;
    private int right;
    private int top;
    private int bottom;
    private int middle;
    private int[] horizontalSpaces;

    public GridSpacingDecoration(int top, int right, int bottom, int left, int middle) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
        this.middle = middle;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
            @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        GridLayoutManager layoutManager = (GridLayoutManager) parent.getLayoutManager();
        int spanCount = getSpanCount(layoutManager);
        int position = parent.getChildAdapterPosition(view); // item position
        int column = layoutManager.getSpanSizeLookup().getSpanIndex(position, spanCount);
        int row = layoutManager.getSpanSizeLookup().getSpanGroupIndex(position, spanCount);

        if (horizontalSpaces == null) {
            int recyclerWith = parent.getWidth();
            horizontalSpaces = initHorizontalSpaces(spanCount, recyclerWith, left, right, middle);
        }
        outRect.left = horizontalSpaces[column * 2];
        outRect.right = horizontalSpaces[column * 2 + 1];
        if (!isFirstRow(row)) {
            outRect.top = middle;
        }
        if (isFirstRow(row)) {
            outRect.top = top;
        }
        if (isLastRow(parent, row)) {
            outRect.bottom = bottom;
        }
    }

    private int getSpanCount(GridLayoutManager layoutManager) {
        return layoutManager.getSpanCount();
    }

    private boolean isFirstRow(int row) {
        return row == 0;
    }

    private boolean isLastRow(RecyclerView recyclerView, int row) {
        return row == getLastRow(recyclerView);
    }

    private int getLastRow(RecyclerView recyclerView) {
        GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
        int spanCount = layoutManager.getSpanCount();
        return layoutManager.getSpanSizeLookup()
                .getSpanGroupIndex(layoutManager.getItemCount() - 1, spanCount);
    }

    private int[] initHorizontalSpaces(int spanCount, int recyclerWidth, int start, int end,
            int middle) {
        int[] horizontalSpaces = new int[spanCount * 2];
        int itemWidthFull = recyclerWidth / spanCount;
        int itemWidthAfterAddSpacing =
                (recyclerWidth - start - end - middle * (spanCount - 1)) / spanCount;

        int i = 0;
        while (i < horizontalSpaces.length) {
            if (i == 0) {
                horizontalSpaces[i] = start;
            } else {
                horizontalSpaces[i] = middle - horizontalSpaces[i - 1];
            }
            horizontalSpaces[i + 1] =
                    itemWidthFull - itemWidthAfterAddSpacing - horizontalSpaces[i];
            i += 2;
        }
        return horizontalSpaces;
    }
}