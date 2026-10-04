package main

import (
	"context"
	"errors"
	"log"
	"net/url"
	"time"

	"github.com/eclipse/paho.golang/autopaho"
	"github.com/eclipse/paho.golang/paho"
)

// broker publishes retained on one topic. Its Last Will clears the topic when the connection drops without a
// disconnect, and close clears it on shutdown, so a dead sampler leaves no figures behind.
type broker struct {
	conn  *autopaho.ConnectionManager
	topic string
}

func connect(ctx context.Context, server *url.URL, topic, clientID string) (*broker, error) {
	conn, err := autopaho.NewConnection(ctx, autopaho.ClientConfig{
		ServerUrls:       []*url.URL{server},
		KeepAlive:        10,
		ReconnectBackoff: autopaho.NewConstantBackoff(5 * time.Second),
		WillMessage:      &paho.WillMessage{Topic: topic, QoS: 1, Retain: true},
		OnConnectionUp:   func(*autopaho.ConnectionManager, *paho.Connack) { log.Printf("connected to %s", server) },
		OnConnectError:   func(err error) { log.Printf("connecting to %s: %v", server, err) },
		ClientConfig:     paho.ClientConfig{ClientID: clientID},
	})
	if err != nil {
		return nil, err
	}
	return &broker{conn: conn, topic: topic}, nil
}

func (b *broker) publish(ctx context.Context, payload []byte) error {
	_, err := b.conn.Publish(ctx, &paho.Publish{Topic: b.topic, QoS: 1, Retain: true, Payload: payload})
	return err
}

func (b *broker) close(ctx context.Context) error {
	return errors.Join(b.publish(ctx, nil), b.conn.Disconnect(ctx))
}
