# ☀️ Weather App  

A simple **Spring Boot** application that fetches real-time weather data using **OpenWeather API** and displays it in a user-friendly interface.  

![Weather App](weather%20app.png)

## 🚀 Features
- ✅ Fetches current weather for any city  
- ✅ Displays temperature, humidity, wind speed, and weather conditions  
- ✅ Uses **OpenWeather API** for real-time data  
- ✅ Built with **Spring Boot & Thymeleaf**  


## 🛠️ Technologies Used
- **Spring Boot** (Backend)  
- **Thymeleaf** (Template engine)  
- **OpenWeather API** (Weather data provider)  
- **Bootstrap & CSS** (Frontend design)  

## ⚙️ Setup Instructions
### 1️⃣ Clone the Repository  
```sh
git clone https://github.com/Hhari1234/Weather-app.git
cd Weather-app
```

### 2️⃣ Add API Key  
Create a `.env` file and add:  
```
API_KEY=your_openweather_api_key
```

### 3️⃣ Run the App  
```sh
mvn spring-boot:run
```
Then open **`http://localhost:8080/`** in your browser.

## 🌍 API Integration
**OpenWeather API** fetches real-time weather data:  
```
http://api.openweathermap.org/data/2.5/weather?q={city}&appid={API_KEY}&units=metric
```

## 🎯 To-Do List  
- [ ] Add **5-day weather forecast**  
- [ ] Implement **location-based weather detection**  
- [ ] Deploy to **Heroku/Vercel**  

## 🐝 .gitignore Configuration
To ignore the `.env` file, add the following to `.gitignore`:
```
.env
```
Then commit the changes:
```sh
git add .gitignore
git commit -m "Ignore .env file"
git push origin main
```


## 📨 Contact  
👉 **GitHub:** [Hhari1234](https://github.com/Hhari1234)  
💎 **Email:** hariraj.2021@vitstudent.ac.in  

