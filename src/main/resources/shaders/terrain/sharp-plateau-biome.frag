#version 330

uniform float borderDistanceScale;
uniform sampler2D riverBorderDistanceMask;
uniform sampler2D coastDistanceMask;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float riverBorderDistance = texture(riverBorderDistanceMask, VertexIn.uv).r;
    float minBorderDist = borderDistanceScale * 0.0016;
    float minBorderDistInverse = 1.0 - minBorderDist;
    if (riverBorderDistance > minBorderDistInverse) {
        colorOut = vec4(0.03, 0.03, 0.03, 1.0);
    } else {
        minBorderDist = borderDistanceScale * 0.003;
        minBorderDistInverse = 1.0 - minBorderDist;
        float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
        if (coastDistance > minBorderDistInverse) {
            colorOut = vec4(0.001, 0.001, 0.001, 1.0);
        } else {
            colorOut = vec4(0.001, 0.001, 0.001, 1.0);
        }
    }
}
