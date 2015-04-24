package com.itextpdf.model;

import com.itextpdf.canvas.color.Color;

public class Property {

    // TODO test which makes sure there is no overlapping in numbers

    private Property() {
    }

    public static final int X = 1;
    public static final int Y = 2;
    public static final int WIDTH = 3;
    public static final int HEIGHT = 4;
    public static final int POSITION = 5;
    public static final int TOP = 6;
    public static final int BOTTOM = 7;
    public static final int LEFT = 8;
    public static final int RIGHT = 9;
    public static final int FONT = 10;
    public static final int BASE_DIRECTION = 11;
    public static final int ALIGNMENT = 12;
    public static final int TEXT_RISE = 13;
    public static final int FONT_SIZE = 14;
    public static final int FONT_COLOR = 15;
    public static final int BACKGROUND = 16;
    public static final int CHARACTER_SPACING = 17;
    public static final int HORIZONTAL_SCALING = 18;
    public static final int TEXT_RENDERING_MODE = 19;
    public static final int STROKE_WIDTH = 20;
    public static final int STROKE_COLOR = 21;
    public static final int FIRST_LINE_INDENT = 22;
    public static final int MARGIN_TOP = 23;
    public static final int MARGIN_BOTTOM = 24;
    public static final int MARGIN_LEFT = 25;
    public static final int MARGIN_RIGHT = 26;
    public static final int PADDING_TOP = 27;
    public static final int PADDING_BOTTOM = 28;
    public static final int PADDING_LEFT = 29;
    public static final int PADDING_RIGHT = 30;

    // TODO some properties are inherited, some are not.


    public enum BaseDirection {
        LTR,
        RTL
    }

    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT,
        JUSTIFIED
    }

    public static final class TextRenderingMode {
        private TextRenderingMode() {}

        public static final int TEXT_RENDERING_MODE_FILL = 0;
        public static final int TEXT_RENDERING_MODE_STROKE = 1;
        public static final int TEXT_RENDERING_MODE_FILL_STROKE = 2;
        public static final int TEXT_RENDERING_MODE_INVISIBLE = 3;
        public static final int TEXT_RENDERING_MODE_FILL_CLIP = 4;
        public static final int TEXT_RENDERING_MODE_STROKE_CLIP = 5;
        public static final int TEXT_RENDERING_MODE_FILL_STROKE_CLIP = 6;
        public static final int TEXT_RENDERING_MODE_CLIP = 7;
    }

    public static class Background {
        protected Color color;
        protected float extraLeft;
        protected float extraRight;
        protected float extraTop;
        protected float extraBottom;

        public Background(Color color, float extraLeft, final float extraTop, final float extraRight, float extraBottom) {
            this.color = color;
            this.extraLeft = extraLeft;
            this.extraRight = extraRight;
            this.extraTop = extraTop;
            this.extraBottom = extraBottom;
        }

        public Color getColor() {
            return color;
        }

        public float getExtraLeft() {
            return extraLeft;
        }

        public float getExtraRight() {
            return extraRight;
        }

        public float getExtraTop() {
            return extraTop;
        }

        public float getExtraBottom() {
            return extraBottom;
        }
    }

}
