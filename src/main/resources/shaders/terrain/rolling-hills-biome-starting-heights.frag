#version 330

uniform float textureScale;
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
    float riverBorderDistance = texture(riverBorderDistanceMask, VertexIn.uv).r;
    float minBorderDist = borderDistanceScale * 0.02;
    float minBorderDistInverse = 1.0 - minBorderDist;
    if (riverBorderDistance > minBorderDistInverse) {
        float rawHeight = texture(noiseMask1, VertexIn.uv * textureScale).r * 0.3;
        float height = (((minBorderDist - (riverBorderDistance - minBorderDistInverse)) * 50 + 0.00000015) * rawHeight) / 300.0;
        colorOut = vec4(height, height, height, 1.0);
    } else {
        minBorderDist = borderDistanceScale * 0.030;
        minBorderDistInverse = 1.0 - minBorderDist;
        float mountainBorderDistance = texture(mountainBorderDistanceMask, VertexIn.uv).r;
        if (mountainBorderDistance > minBorderDistInverse) {
            float multiplier = (mountainBorderDistance - minBorderDistInverse) * 35 + 0.05;
            float height = (texture(noiseMask1, VertexIn.uv * textureScale).r * multiplier) / 300.0;
            colorOut = vec4(height, height, height, 1.0);
        } else {
            minBorderDist = borderDistanceScale * 0.004;
            minBorderDistInverse = 1.0 - minBorderDist;
            float coastDistance = texture(coastDistanceMask, VertexIn.uv).r;
            if (coastDistance > minBorderDistInverse) {
//                colorOut = vec4(0.0000001, 0.0000001, 0.0000001, 1.0);
                float height = (texture(noiseMask1, VertexIn.uv * textureScale).r * 0.275) / 300.0;
                colorOut = vec4(height, height, height, 1.0);
            } else {
                float height = (texture(noiseMask1, VertexIn.uv * textureScale).r * 0.275) / 300.0;
                colorOut = vec4(height, height, height, 1.0);
            }
        }
    }
}
