#version 330

uniform float borderDistanceScale;
uniform sampler2D riverBorderDistanceMask;
uniform sampler2D mountainBorderDistanceMask;
uniform sampler2D coastDistanceMask;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float riverBorderDistance = texture(riverBorderDistanceMask, VertexIn.uv).r;
    float minBorderDist = borderDistanceScale * 0.01;
    float minBorderDistInverse = 1.0 - minBorderDist;
    if (riverBorderDistance > minBorderDistInverse) {
        float height = ((minBorderDist - (riverBorderDistance - minBorderDistInverse)) * 35 + 0.00001) * 0.9;
        colorOut = vec4(height, height, height, 1.0);
    } else {
        float mountainBorderDistance = texture(mountainBorderDistanceMask, VertexIn.uv).r;
        if (mountainBorderDistance > minBorderDistInverse) {
            colorOut = vec4(0.9, 0.9, 0.9, 1.0);
        } else {
            float regionBorderDistance = max(riverBorderDistance, mountainBorderDistance);
            if (regionBorderDistance > 1.0 - (0.05 * borderDistanceScale)) {
                colorOut = vec4(0.82, 0.82, 0.82, 1.0);
            } else {
                float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
                if (coastDistance > 1.0 - (0.001 * borderDistanceScale)) {
                    colorOut = vec4(0.009, 0.009, 0.009, 1.0);
                } else if (coastDistance > 0.97) {
//                    colorOut = vec4(0.001, 0.001, 0.001, 1.0);
                    colorOut = vec4(0.4, 0.4, 0.4, 1.0);
                } else {
                    colorOut = vec4(0.4, 0.4, 0.4, 1.0);
                }
            }
        }
    }
}
