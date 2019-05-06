#version 330

uniform float heightScale;
uniform float borderDistanceScale;
uniform sampler2D riverBorderDistanceMask;
uniform sampler2D coastDistanceMask;
uniform sampler2D noiseMask1;

in VertexData {
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

void main() {
    float noise = (texture(noiseMask1, VertexIn.uv).r - 0.5) * 0.00025;
    float riverBorderDistance = texture(riverBorderDistanceMask, VertexIn.uv).r;
    float minBorderDist = borderDistanceScale * 0.0016;
    float minBorderDistInverse = 1.0 - minBorderDist;
    if (riverBorderDistance > minBorderDistInverse) {
        float height = 0.000007 + noise;
        colorOut = vec4(height, height, height, 1.0);
    } else {
        minBorderDist = borderDistanceScale * 0.003;
        minBorderDistInverse = 1.0 - minBorderDist;
        float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
        if (coastDistance > minBorderDistInverse) {
            colorOut = vec4(0.0000001, 0.0000001, 0.0000001, 1.0);
        } else {
            float height = (0.09 + noise) * heightScale;
            colorOut = vec4(height, height, height, 1.0);
        }
    }
}
