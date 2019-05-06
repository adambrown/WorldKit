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
    float minBorderDist = borderDistanceScale * 0.001;
    float minBorderDistInverse = 1.0 - minBorderDist;
    if (riverBorderDistance > minBorderDistInverse) {
        float height = ((minBorderDist - (riverBorderDistance - minBorderDistInverse)) * 15 + 0.00001) * 0.022;
        colorOut = vec4(height, height, height, 1.0);
    } else {
        minBorderDist = borderDistanceScale * 0.03;
        minBorderDistInverse = 1.0 - minBorderDist;
        float mountainBorderDistance = texture(mountainBorderDistanceMask, VertexIn.uv).r;
        if (mountainBorderDistance > minBorderDistInverse) {
            float height = (((mountainBorderDistance - minBorderDistInverse) * 33 + 0.00001) * 0.12 + 0.88) * 0.022;
            colorOut = vec4(height, height, height, 1.0);
        } else {
            minBorderDist = borderDistanceScale * 0.001;
            minBorderDistInverse = 1.0 - minBorderDist;
            float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
            if (coastDistance > minBorderDistInverse) {
                colorOut = vec4(0.001, 0.001, 0.001, 1.0);
            } else {
                colorOut = vec4(0.02, 0.02, 0.02, 1.0);
            }
        }
    }
}
