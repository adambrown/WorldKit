#version 330

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform float heightScale;
uniform float waterLevel;

in vec2 position;
in vec2 uv;

out VertexData {
    vec3 position;
} VertexOut;

void main () {
    vec4 pos4 = vec4(position.xy, heightScale * waterLevel, 1.0);
    VertexOut.position = pos4.xyz;
    gl_Position = modelViewProjectionMatrix * pos4;
}