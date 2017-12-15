#version 330

uniform mat4 modelViewProjectionMatrix;
uniform sampler2D imageTexture;

in vec2 position;
in vec2 uv;

in vec4 instancePosition;

out VertexData {
    vec2 uv;
} VertexOut;

void main () {
    float width = instancePosition.z;
    float height = instancePosition.w;
    ivec2 imageTextureSize = textureSize(imageTexture, 0);
    VertexOut.uv = vec2((uv.x * width) / imageTextureSize.x, (uv.y * height) / imageTextureSize.y);
    gl_Position = modelViewProjectionMatrix * vec4(instancePosition.xy + (position * instancePosition.zw), 0.0f, 1.0f);
}