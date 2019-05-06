#version 330

uniform float borderDistanceScale;
uniform sampler2D riverBorderDistanceMask;
uniform sampler2D mountainBorderDistanceMask;
uniform sampler2D coastDistanceMask;
uniform sampler2D noiseMask1;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float noise = (texture(noiseMask1, VertexIn.uv).r - 0.5) * 0.0005;
    float riverBorderDistance = texture(riverBorderDistanceMask, VertexIn.uv).r;
    float minBorderDist = borderDistanceScale * 0.01;
    float minBorderDistInverse = 1.0 - minBorderDist;
    if (riverBorderDistance > minBorderDistInverse) {
        float height = (((minBorderDist - (riverBorderDistance - minBorderDistInverse)) * 35 + 0.00001) / 300.0) + noise;
        colorOut = vec4(height, height, height, 1.0);
    } else {
        float mountainBorderDistance = texture(mountainBorderDistanceMask, VertexIn.uv).r;
        if (mountainBorderDistance > minBorderDistInverse) {
            float height = 0.0033333 + noise;
            colorOut = vec4(height, height, height, 1.0);
        } else {
            float regionBorderDistance = max(riverBorderDistance, mountainBorderDistance);
            if (regionBorderDistance > 1.0 - (0.05 * borderDistanceScale)) {
                float height = 0.003 + noise;
                colorOut = vec4(height, height, height, 1.0);
            } else {
                float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
                if (coastDistance > 1.0 - (0.001 * borderDistanceScale)) {
                    colorOut = vec4(0.0000001, 0.0000001, 0.0000001, 1.0);
                } else if (coastDistance > 0.97) {
                    float height = 0.00083333 + noise;
                    colorOut = vec4(height, height, height, 1.0);
                } else {
                    float height = 0.0015 + noise;
                    colorOut = vec4(height, height, height, 1.0);
                }
            }
        }
    }
}
