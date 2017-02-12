#version 330

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat3 normalMatrix;
uniform vec3 lightDirection;
uniform float heightScale;

in vec2 position;
in vec2 uv;

out VertexData {
    vec3 position;
    vec3 normal;
} VertexOut;

void main () {
    vec4 pos4 = vec4(position.xy, heightScale * 0.29944, 1.0);
    VertexOut.position = vec3(modelViewMatrix * pos4);
    VertexOut.normal = normalMatrix * vec3(0.0f, 0.0f, 1.0f);
    gl_Position = modelViewProjectionMatrix * pos4;
}