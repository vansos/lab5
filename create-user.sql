create table if not exist users(
    id int primary key auto_increment,
    name varchar(50) not null,
    surname varchar(100) not null,
    subscribed boolean not null default false,
    phone varchar(15) not null
);

create table if not exist books(
    id int primary key auto_increment,
    name varchar(100),
    isbn varchar(100),
    publishing_year varchar(100),
    author varchar(100),
    publisher varchar(100)
);